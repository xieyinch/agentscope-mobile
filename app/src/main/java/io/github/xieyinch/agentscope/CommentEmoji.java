package io.github.xieyinch.agentscope;

import android.graphics.BitmapFactory;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

/** Renders the community's inline emoji tokens with their original CDN images. */
final class CommentEmoji {
    private static final Pattern TOKEN = Pattern.compile("\\[emoji:([^\\]\\r\\n]*)\\]");
    private static final String PLACEHOLDER = "[表情]";
    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIMENSION = 1024;
    // The media client has no access to the account's cookies or authorization headers.
    private final OkHttpClient client;

    CommentEmoji() {
        this(new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(12, TimeUnit.SECONDS)
            .build());
    }

    CommentEmoji(OkHttpClient client) {
        this.client = client;
    }

    void bind(TextView view, String content, int size, Runnable onLayoutNeeded) {
        Matcher tokens = TOKEN.matcher(content);
        if (!tokens.find()) {
            view.setText(content);
            return;
        }

        SpannableStringBuilder text = new SpannableStringBuilder();
        List<Emoji> emojis = new ArrayList<>();
        int end = 0;
        do {
            text.append(content, end, tokens.start());
            int start = text.length();
            text.append(PLACEHOLDER);
            HttpUrl url = HttpUrl.parse(tokens.group(1));
            if (url != null && url.isHttps() && url.host().equals("img.alicdn.com")
                    && url.port() == 443 && url.username().isEmpty() && url.password().isEmpty()
                    && url.encodedPath().startsWith("/imgextra/")) {
                emojis.add(new Emoji(start, text.length(), url));
            }
            end = tokens.end();
        } while (tokens.find());
        text.append(content, end, content.length());
        view.setText(text, TextView.BufferType.SPANNABLE);

        View.OnAttachStateChangeListener binding = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                RequestManager requests = Glide.with(view);
                for (Emoji emoji : emojis) load(view, emoji, size, onLayoutNeeded, requests);
            }

            @Override public void onViewDetachedFromWindow(View v) {
                for (Emoji emoji : emojis) {
                    if (emoji.call != null) emoji.call.cancel();
                    if (emoji.target != null) {
                        emoji.requests.clear(emoji.target);
                        emoji.target = null;
                        emoji.requests = null;
                    }
                }
            }
        };
        if (!emojis.isEmpty()) {
            view.addOnAttachStateChangeListener(binding);
            if (view.isAttachedToWindow()) binding.onViewAttachedToWindow(view);
        }
    }

    private void load(TextView view, Emoji emoji, int size, Runnable onLayoutNeeded, RequestManager requests) {
        Call call = client.newCall(new Request.Builder().url(emoji.url).build());
        emoji.call = call;
        call.enqueue(new Callback() {
            @Override public void onFailure(Call ignored, IOException error) {
                // The readable placeholder remains in the comment.
            }

            @Override public void onResponse(Call request, Response response) {
                try (response) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    BufferedSource source = response.body().source();
                    if (source.request(MAX_BYTES + 1)) return;
                    byte[] bytes = source.readByteArray();
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    int longest = Math.max(bounds.outWidth, bounds.outHeight);
                    if (longest <= 0 || longest > MAX_DIMENSION) return;
                    view.post(() -> {
                        if (call.isCanceled() || !view.isAttachedToWindow()) return;
                        showImage(view, emoji, size, onLayoutNeeded, requests, bytes);
                    });
                } catch (IOException | RuntimeException ignored) {
                    // An unavailable or malformed image leaves the placeholder in place.
                }
            }
        });
    }

    private void showImage(TextView view, Emoji emoji, int size, Runnable onLayoutNeeded,
                           RequestManager requests, byte[] bytes) {
        Spannable displayed = (Spannable) view.getText();
        emoji.requests = requests;
        emoji.target = new CustomTarget<Drawable>(size, size) {
            private ImageSpan span;

            @Override public void onResourceReady(@NonNull Drawable image,
                                                  @Nullable Transition<? super Drawable> transition) {
                float scale = (float) size / Math.max(image.getIntrinsicWidth(), image.getIntrinsicHeight());
                image.setBounds(0, 0, Math.max(1, Math.round(image.getIntrinsicWidth() * scale)),
                    Math.max(1, Math.round(image.getIntrinsicHeight() * scale)));
                span = new ImageSpan(image, ImageSpan.ALIGN_BASELINE);
                displayed.setSpan(span, emoji.start, emoji.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                image.setCallback(new Drawable.Callback() {
                    @Override public void invalidateDrawable(Drawable drawable) { view.invalidate(); }

                    @Override public void scheduleDrawable(Drawable drawable, Runnable action, long when) {
                        view.postDelayed(action, Math.max(0, when - SystemClock.uptimeMillis()));
                    }

                    @Override public void unscheduleDrawable(Drawable drawable, Runnable action) {
                        view.removeCallbacks(action);
                    }
                });
                onStart();
                view.post(onLayoutNeeded);
            }

            @Override public void onStart() {
                if (span != null && span.getDrawable() instanceof Animatable) {
                    ((Animatable) span.getDrawable()).start();
                }
            }

            @Override public void onStop() {
                if (span != null && span.getDrawable() instanceof Animatable) {
                    ((Animatable) span.getDrawable()).stop();
                }
            }

            @Override public void onLoadCleared(@Nullable Drawable placeholder) {
                if (span == null) return;
                onStop();
                span.getDrawable().setCallback(null);
                displayed.removeSpan(span);
                span = null;
            }
        };
        requests.asDrawable().load(bytes).diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true).into(emoji.target);
    }

    private static final class Emoji {
        final int start;
        final int end;
        final HttpUrl url;
        Call call;
        CustomTarget<Drawable> target;
        RequestManager requests;

        Emoji(int start, int end, HttpUrl url) {
            this.start = start;
            this.end = end;
            this.url = url;
        }
    }
}
