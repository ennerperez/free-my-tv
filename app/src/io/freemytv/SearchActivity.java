package io.freemytv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** TV-friendly TMDB search: movies and series plus streaming availability in the configured country. */
public class SearchActivity extends Activity {
    private static final long DEBOUNCE_MS = 550;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText query;
    private TextView status;
    private LinearLayout results;
    private Recs recs;
    private Runnable pending;
    private int generation;
    private float density;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        recs = Recs.get(this);
        density = getResources().getDisplayMetrics().density;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(content());
        query.requestFocus();
        query.postDelayed(this::showKeyboard, 250);
    }

    private View content() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(34), dp(56), dp(28));
        root.setBackgroundColor(Color.rgb(13, 14, 18));

        TextView title = text(Ui.tr("Search TMDB"), 34, Color.WHITE, Ui.displaySemibold);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));

        query = new EditText(this);
        query.setSingleLine(true);
        query.setTextSize(22);
        query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xFF97979F);
        query.setHint(Ui.tr("Movie or series"));
        query.setPadding(dp(24), 0, dp(24), 0);
        query.setTypeface(Ui.regular);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setBackground(round(0xFF2A2B31, 20, 0xFF6A6B73, 2));
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(-1, dp(66));
        queryLp.bottomMargin = dp(16);
        root.addView(query, queryLp);

        status = text(Ui.tr("Type to search movies and series."), 17, 0xFFB7B7BE, Ui.regular);
        status.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(38)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(6), 0, dp(24));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView credit = text(Ui.tr("Results and availability provided by TMDB."), 14, 0xFF777780, Ui.regular);
        root.addView(credit, new LinearLayout.LayoutParams(-1, dp(24)));

        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleSearch(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                searchNow();
                hideKeyboard();
                return true;
            }
            return false;
        });
        query.setOnFocusChangeListener((v, focused) -> {
            query.setBackground(round(focused ? 0xFF34353C : 0xFF2A2B31, 20,
                    focused ? 0xFFFFFFFF : 0xFF6A6B73, focused ? 3 : 2));
            if (focused) showKeyboard();
        });
        return root;
    }

    private void scheduleSearch() {
        generation++;
        if (pending != null) handler.removeCallbacks(pending);
        String value = query.getText().toString().trim();
        if (value.isEmpty()) {
            results.removeAllViews();
            status.setText(Ui.tr("Type to search movies and series."));
            return;
        }
        status.setText(Ui.tr("Waiting…"));
        pending = this::searchNow;
        handler.postDelayed(pending, DEBOUNCE_MS);
    }

    private void searchNow() {
        if (pending != null) handler.removeCallbacks(pending);
        String value = query.getText().toString().trim();
        if (value.isEmpty()) return;
        if (!Recs.hasKey(this)) {
            status.setText(Ui.tr("Add tmdb_key to config.properties."));
            results.removeAllViews();
            return;
        }
        final int ticket = ++generation;
        status.setText(Ui.tr("Searching TMDB…"));
        recs.search(value, (found, error) -> {
            if (ticket != generation || isFinishing() || isDestroyed()) return;
            showResults(found, error);
        });
    }

    private void showResults(List<Recs.SearchResult> found, String error) {
        results.removeAllViews();
        if (!error.isEmpty()) {
            status.setText(Ui.trf("Couldn't search: {error}", "error", error));
            return;
        }
        if (found.isEmpty()) {
            status.setText(Ui.tr("No movies or series found."));
            return;
        }
        status.setText(Ui.trf("{n} results · region {region}", "n", found.size(), "region", Recs.REGION));
        for (Recs.SearchResult result : found) results.addView(resultCard(result));
    }

    private View resultCard(Recs.SearchResult result) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(14), dp(22), dp(14));
        card.setFocusable(true);
        card.setBackground(round(0xFF24252B, 18, 0xFF3A3B43, 1));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(round(0xFF34353C, 12, 0xFF45464E, 1));
        poster.setClipToOutline(true);
        LinearLayout.LayoutParams posterLp = new LinearLayout.LayoutParams(dp(84), dp(126));
        posterLp.rightMargin = dp(20);
        card.addView(poster, posterLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));

        TextView name = text(result.title, 22, Color.WHITE, Ui.semibold);
        TextView meta = text(("movie".equals(result.kind) ? Ui.tr("Movie") : Ui.tr("Series"))
                + (result.year.isEmpty() ? "" : " · " + result.year),
                15, 0xFFAAAAAF, Ui.medium);
        String providerText = result.providers.isEmpty()
                ? Ui.trf("No streaming availability in region {region}", "region", Recs.REGION)
                : Ui.trf("Available on: {providers}", "providers", String.join(", ", result.providers));
        TextView providers = text(providerText, 17, result.providers.isEmpty() ? 0xFF9999A1 : 0xFF67D583, Ui.medium);
        providers.setMaxLines(2);
        providers.setEllipsize(android.text.TextUtils.TruncateAt.END);

        copy.addView(name, new LinearLayout.LayoutParams(-1, dp(30)));
        copy.addView(meta, new LinearLayout.LayoutParams(-1, dp(24)));
        copy.addView(providers, new LinearLayout.LayoutParams(-1, -2));
        if (!result.overview.isEmpty()) {
            TextView overview = text(result.overview, 15, 0xFFB8B8BE, Ui.regular);
            overview.setMaxLines(2);
            overview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(overview, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        recs.poster(result.poster, image -> {
            if (!isFinishing() && !isDestroyed()) poster.setImageBitmap(image);
        });
        card.setContentDescription(result.title + ". " + providerText);
        card.setOnFocusChangeListener((v, focused) -> {
            card.setBackground(round(focused ? 0xFFF5F5F7 : 0xFF24252B, 18,
                    focused ? 0xFFFFFFFF : 0xFF3A3B43, focused ? 2 : 1));
            name.setTextColor(focused ? 0xFF1C1C1E : Color.WHITE);
            meta.setTextColor(focused ? 0xFF5A5A60 : 0xFFAAAAAF);
            providers.setTextColor(focused ? 0xFF147D32 : result.providers.isEmpty() ? 0xFF9999A1 : 0xFF67D583);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView text(String value, float size, int color, Typeface face) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(face);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return drawable;
    }

    private int dp(float value) { return Math.round(value * density); }

    private void showKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && query != null) keyboard.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && query != null) keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    @Override protected void onDestroy() {
        generation++;
        if (pending != null) handler.removeCallbacks(pending);
        super.onDestroy();
    }
}
