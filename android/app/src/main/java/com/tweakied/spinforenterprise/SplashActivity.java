package com.tweakied.spinforenterprise;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int[] RAINBOW_COLORS = {
            0xFFFF0000, 0xFFFF7F00, 0xFFFFFF00, 0xFF00FF00,
            0xFF0000FF, 0xFF4B0082, 0xFF9400D3, 0xFFFF0000
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splashLogo);
        TextView title = findViewById(R.id.splashTitle);
        View progressBar = findViewById(R.id.splashProgress);

        logo.setAlpha(0f);
        logo.setScaleX(0.3f);
        logo.setScaleY(0.3f);
        title.setAlpha(0f);
        title.setTranslationY(60f);
        progressBar.setScaleX(0f);

        // Phase 1: Logo appears with scale + fade animation
        ObjectAnimator logoFade = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.3f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.3f, 1f);

        AnimatorSet logoAnim = new AnimatorSet();
        logoAnim.playTogether(logoFade, logoScaleX, logoScaleY);
        logoAnim.setDuration(1200);
        logoAnim.setInterpolator(new OvershootInterpolator(1.5f));

        // Phase 2: Progress bar loads
        ObjectAnimator progressAnim = ObjectAnimator.ofFloat(progressBar, "scaleX", 0f, 1f);
        progressAnim.setDuration(1500);
        progressAnim.setInterpolator(new AccelerateDecelerateInterpolator());

        // Phase 3: Title reveals with rainbow gradient
        ObjectAnimator titleFade = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f);
        ObjectAnimator titleSlide = ObjectAnimator.ofFloat(title, "translationY", 60f, 0f);

        AnimatorSet titleAnim = new AnimatorSet();
        titleAnim.playTogether(titleFade, titleSlide);
        titleAnim.setDuration(800);
        titleAnim.setInterpolator(new AccelerateDecelerateInterpolator());

        // Chain animations
        logoAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                progressAnim.start();
            }
        });

        progressAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startRainbowText(title);
                titleAnim.start();
            }
        });

        titleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Wait a moment then launch main activity
                title.postDelayed(() -> {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }, 800);
            }
        });

        // Start the animation chain
        logo.postDelayed(logoAnim::start, 400);
    }

    private void startRainbowText(TextView textView) {
        textView.post(() -> {
            float width = textView.getPaint().measureText(textView.getText().toString());
            if (width <= 0f) width = 600f;

            final float gradientWidth = width;
            LinearGradient gradient = new LinearGradient(
                    0, 0, gradientWidth, 0,
                    RAINBOW_COLORS, null, Shader.TileMode.MIRROR);
            textView.getPaint().setShader(gradient);

            Matrix matrix = new Matrix();
            ValueAnimator animator = ValueAnimator.ofFloat(0f, gradientWidth * 2f);
            animator.setDuration(2000L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                float translate = (float) animation.getAnimatedValue();
                matrix.setTranslate(translate, 0f);
                gradient.setLocalMatrix(matrix);
                textView.invalidate();
            });
            animator.start();
        });
    }
}
