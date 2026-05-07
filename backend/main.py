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
    return {"users": {}, "apps": [], "tasks": [], "conversations": [], "bans": [], "alert_message": "", "user_spins": {}}

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
    android_id: Optional[str] = None

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
    reward_spins: int = 1
    countdown_minutes: Optional[int] = None

class TaskAction(BaseModel):
    action: str  # "approve" or "deny"

class ClaimTask(BaseModel):
    task_id: str

class AlertMessage(BaseModel):
    message: str

class GiveSpins(BaseModel):
    spins: int = 1

# --- Auth ---
# --- Load variables from variables.txt if present ---
def load_variables():
    vars_file = Path("variables.txt")
    if not vars_file.exists():
        vars_file = Path(__file__).parent / "variables.txt"
    if vars_file.exists():
        with open(vars_file) as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, _, value = line.partition("=")
                    os.environ.setdefault(key.strip(), value.strip())

load_variables()

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

    # Use android_id if available (persists across reinstalls), fallback to IP hash
    if info.android_id:
        user_id = hashlib.md5(info.android_id.encode()).hexdigest()[:12]
    else:
        user_id = hashlib.md5(client_ip.encode()).hexdigest()[:12]

    # Check if this android_id was previously registered under a different user_id (IP-based)
    # Migrate data if so
    if info.android_id:
        ip_based_id = hashlib.md5(client_ip.encode()).hexdigest()[:12]
        if ip_based_id != user_id and ip_based_id in db["users"]:
            old_data = db["users"][ip_based_id]
            # Migrate spins
            if ip_based_id in db.get("user_spins", {}):
                old_spins = db["user_spins"].pop(ip_based_id, 0)
                db["user_spins"][user_id] = db.get("user_spins", {}).get(user_id, 0) + old_spins
            # Migrate claimed tasks
            if ip_based_id in db.get("claimed_tasks", {}):
                old_claimed = db["claimed_tasks"].pop(ip_based_id, [])
                if user_id not in db.get("claimed_tasks", {}):
                    db["claimed_tasks"][user_id] = []
                db["claimed_tasks"][user_id].extend(old_claimed)

    user_data = {
        "user_id": user_id,
        "android_id": info.android_id,
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
    user_spins = db.get("user_spins", {})
    for u in users:
        u["banned"] = u["user_id"] in bans
        u["bonus_spins"] = user_spins.get(u["user_id"], 0)
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


@app.delete("/api/admin/apps/{app_id}")
async def delete_app(app_id: str, _=Depends(verify_admin)):
    db = load_db()
    db["apps"] = [a for a in db["apps"] if a["id"] != app_id]
    save_db(db)
    return {"status": "deleted"}


@app.post("/api/admin/tasks")
async def add_task(task: TaskItem, _=Depends(verify_admin)):
    db = load_db()
    now = datetime.now(timezone.utc)
    expires_at = None
    if task.countdown_minutes and task.countdown_minutes > 0:
        from datetime import timedelta
        expires_at = (now + timedelta(minutes=task.countdown_minutes)).isoformat()
    db["tasks"].append({
        "id": uuid.uuid4().hex[:8],
        "title": task.title,
        "description": task.description,
        "reward_spins": task.reward_spins,
        "countdown_minutes": task.countdown_minutes,
        "expires_at": expires_at,
        "added_at": now.isoformat(),
        "submissions": {},
    })
    save_db(db)
    return {"status": "added"}


@app.get("/api/admin/tasks")
async def get_tasks(_=Depends(verify_admin)):
    db = load_db()
    tasks = db.get("tasks", [])
    now = datetime.now(timezone.utc).isoformat()
    active = [t for t in tasks if not t.get("expires_at") or t["expires_at"] > now]
    return {"tasks": active}


@app.delete("/api/admin/tasks/{task_id}")
async def delete_task(task_id: str, _=Depends(verify_admin)):
    db = load_db()
    db["tasks"] = [t for t in db["tasks"] if t["id"] != task_id]
    save_db(db)
    return {"status": "deleted"}


@app.post("/api/admin/tasks/{task_id}/user/{user_id}")
async def task_user_action(task_id: str, user_id: str, body: TaskAction, _=Depends(verify_admin)):
    db = load_db()
    for task in db["tasks"]:
        if task["id"] == task_id:
            if "submissions" not in task:
                task["submissions"] = {}
            if body.action == "approve":
                task["submissions"][user_id] = "approved"
            elif body.action == "deny":
                task["submissions"][user_id] = "denied"
            save_db(db)
            return {"status": body.action + "d"}
    raise HTTPException(status_code=404, detail="Task not found")


@app.get("/api/tasks/{user_id}")
async def get_user_tasks(user_id: str):
    db = load_db()
    tasks = db.get("tasks", [])
    now = datetime.now(timezone.utc).isoformat()
    result = []
    claimed = db.get("claimed_tasks", {}).get(user_id, [])
    for t in tasks:
        if t.get("expires_at") and t["expires_at"] <= now:
            continue
        if t["id"] in claimed:
            continue
        status = t.get("submissions", {}).get(user_id, "pending")
        result.append({
            "id": t["id"],
            "title": t["title"],
            "description": t["description"],
            "reward_spins": t["reward_spins"],
            "expires_at": t.get("expires_at"),
            "status": status,
        })
    return {"tasks": result}


@app.post("/api/tasks/submit/{task_id}/{user_id}")
async def submit_task(task_id: str, user_id: str):
    db = load_db()
    for task in db["tasks"]:
        if task["id"] == task_id:
            if "submissions" not in task:
                task["submissions"] = {}
            if task["submissions"].get(user_id) not in ("approved", None):
                return {"status": "already_submitted"}
            if user_id not in task["submissions"]:
                task["submissions"][user_id] = "submitted"
                save_db(db)
            return {"status": "submitted"}
    raise HTTPException(status_code=404, detail="Task not found")


@app.post("/api/tasks/claim/{task_id}/{user_id}")
async def claim_task(task_id: str, user_id: str):
    db = load_db()
    for task in db["tasks"]:
        if task["id"] == task_id:
            status = task.get("submissions", {}).get(user_id, "pending")
            if status != "approved":
                return {"status": "not_approved"}
            # Give spins
            if "user_spins" not in db:
                db["user_spins"] = {}
            current = db["user_spins"].get(user_id, 0)
            db["user_spins"][user_id] = current + task.get("reward_spins", 1)
            # Mark claimed
            if "claimed_tasks" not in db:
                db["claimed_tasks"] = {}
            if user_id not in db["claimed_tasks"]:
                db["claimed_tasks"][user_id] = []
            db["claimed_tasks"][user_id].append(task_id)
            save_db(db)
            return {"status": "claimed", "spins_awarded": task.get("reward_spins", 1)}
    raise HTTPException(status_code=404, detail="Task not found")


@app.get("/api/admin/conversations")
async def get_conversations(_=Depends(verify_admin)):
    db = load_db()
    return {"conversations": db.get("conversations", [])}


# --- Alert Message ---

@app.get("/api/admin/alert-message")
async def get_alert_message(_=Depends(verify_admin)):
    db = load_db()
    return {"message": db.get("alert_message", "")}


@app.post("/api/admin/alert-message")
async def set_alert_message(alert: AlertMessage, _=Depends(verify_admin)):
    db = load_db()
    db["alert_message"] = alert.message
    save_db(db)
    return {"status": "updated"}


@app.get("/api/alert-message")
async def get_alert_message_public():
    db = load_db()
    return {"message": db.get("alert_message", "")}


# --- Give Spins ---

@app.post("/api/admin/give-spins/{user_id}")
async def give_spins(user_id: str, body: GiveSpins, _=Depends(verify_admin)):
    db = load_db()
    if "user_spins" not in db:
        db["user_spins"] = {}
    current = db["user_spins"].get(user_id, 0)
    db["user_spins"][user_id] = current + body.spins
    save_db(db)
    return {"status": "ok", "total_spins": db["user_spins"][user_id]}


@app.get("/api/spins/{user_id}")
async def get_user_spins(user_id: str):
    db = load_db()
    spins = db.get("user_spins", {}).get(user_id, 0)
    return {"spins": spins}


@app.post("/api/use-spin/{user_id}")
async def use_spin(user_id: str):
    db = load_db()
    spins = db.get("user_spins", {}).get(user_id, 0)
    if spins <= 0:
        return {"status": "no_spins", "spins": 0}
    if "user_spins" not in db:
        db["user_spins"] = {}
    db["user_spins"][user_id] = spins - 1
    save_db(db)
    return {"status": "ok", "spins": db["user_spins"][user_id]}


@app.get("/api/health")
async def health():
    return {"status": "ok", "time": datetime.now(timezone.utc).isoformat()}
