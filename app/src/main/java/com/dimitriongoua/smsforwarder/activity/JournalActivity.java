package com.dimitriongoua.smsforwarder.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.FontRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.JournalBadges;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.journal.JournalEntry;
import com.dimitriongoua.smsforwarder.journal.JournalFormat;
import com.dimitriongoua.smsforwarder.journal.JournalQuery;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Journal des SMS traités ces 30 derniers jours (maquette « SMS Forwarder — refonte ») :
 * recherche dans l'expéditeur et le texte, filtres Tout / En attente / Échecs / par SIM,
 * SMS groupés par jour avec l'état de chaque envoi en étiquettes. Chargé page par page en fin
 * de liste ; toucher un SMS ouvre son Détail ({@link DetailActivity}).
 */
public class JournalActivity extends AppCompatActivity {
    /** Ouvre le journal filtré sur les envois en attente (alerte de l'Accueil). */
    public static final String EXTRA_PENDING = "pending";
    private static final int PAGE_SIZE = 30;
    private static final long SEARCH_DELAY_MS = 250;

    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<JournalEntry> entries = new ArrayList<>();
    private final Runnable searchReload = this::reload;
    private EntryAdapter adapter;
    private TextView empty;
    private ChipGroup filters;
    private JournalQuery query = JournalQuery.ALL;
    private boolean loading;
    private boolean hasMore = true;
    // Incrémenté à chaque rechargement : une page d'une ancienne liste est ignorée.
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);
        setTitle(R.string.journal_title);
        TabBar.bind(this, TabBar.Tab.JOURNAL);
        if (getIntent().getBooleanExtra(EXTRA_PENDING, false)) query = query.withStatus(JournalQuery.Status.OPEN);
        empty = findViewById(R.id.journal_empty);
        filters = findViewById(R.id.journal_filters);

        EditText search = findViewById(R.id.journal_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = query.withText(s.toString());
                main.removeCallbacks(searchReload);
                main.postDelayed(searchReload, SEARCH_DELAY_MS);
            }
        });

        ListView list = findViewById(R.id.journal_list);
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> startActivity(
                new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, entries.get(position).id)));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 5) loadMore();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadFilters();
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(searchReload);
        reader.shutdownNow();
    }

    /** Filtres : Tout, En attente (nombre d'envois à faire), Échecs, puis une puce par SIM. */
    private void reloadFilters() {
        reader.execute(() -> {
            JournalDb journal = JournalDb.get(this);
            int open = journal.countOpen();
            Map<Integer, String> sims = journal.sims();
            main.post(() -> {
                if (isFinishing()) return;
                filters.setOnCheckedStateChangeListener(null);
                filters.removeAllViews();
                addFilter(getString(R.string.journal_filter_all), JournalQuery.ALL.withText(query.text));
                addFilter(open > 0 ? getString(R.string.journal_filter_open_count, open)
                        : getString(R.string.journal_filter_open), query.withStatus(JournalQuery.Status.OPEN));
                addFilter(getString(R.string.journal_filter_failed), query.withStatus(JournalQuery.Status.FAILED));
                if (sims.size() > 1) {
                    for (Map.Entry<Integer, String> sim : sims.entrySet()) {
                        addFilter(sim.getValue(), query.withSim(sim.getKey()));
                    }
                }
                filters.setOnCheckedStateChangeListener((group, checked) -> {
                    if (checked.isEmpty()) return;
                    JournalQuery picked = (JournalQuery) group.findViewById(checked.get(0)).getTag();
                    query = picked.withText(query.text);
                    reload();
                });
            });
        });
    }

    private void addFilter(String label, JournalQuery target) {
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(target);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setTypeface(font(R.font.plex_sans_medium));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setChipMinHeight(dp(36));
        chip.setEnsureMinTouchTargetSize(true);
        int[][] states = {{android.R.attr.state_checked}, {}};
        chip.setChipBackgroundColor(new ColorStateList(states, new int[]{color(R.color.ink), color(R.color.surface)}));
        chip.setTextColor(new ColorStateList(states, new int[]{color(R.color.surface), color(R.color.ink)}));
        chip.setChipStrokeColor(new ColorStateList(states, new int[]{color(R.color.ink), color(R.color.line)}));
        chip.setChipStrokeWidth(dp(1));
        chip.setRippleColor(ColorStateList.valueOf(color(R.color.line_soft)));
        filters.addView(chip);
        if (sameFilter(target, query)) chip.setChecked(true);
    }

    private static boolean sameFilter(JournalQuery a, JournalQuery b) {
        return a.status == b.status
                && (a.subscriptionId == null ? b.subscriptionId == null : a.subscriptionId.equals(b.subscriptionId));
    }

    private void reload() {
        generation++;
        entries.clear();
        hasMore = true;
        loading = false;
        adapter.notifyDataSetChanged();
        loadMore();
    }

    private void loadMore() {
        if (loading || !hasMore) return;
        loading = true;
        final int current = generation;
        final JournalQuery currentQuery = query;
        final JournalEntry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        reader.execute(() -> {
            JournalDb journal = JournalDb.get(this);
            if (last == null) journal.purge(System.currentTimeMillis());
            List<JournalEntry> page = journal.page(last, PAGE_SIZE, currentQuery);
            main.post(() -> {
                if (current != generation || isFinishing()) return;
                entries.addAll(page);
                hasMore = page.size() == PAGE_SIZE;
                loading = false;
                boolean filtered = currentQuery.status != JournalQuery.Status.ALL
                        || currentQuery.subscriptionId != null || currentQuery.text != null;
                empty.setText(filtered ? R.string.journal_no_match : R.string.journal_empty);
                empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private Typeface font(@FontRes int id) {
        return ResourcesCompat.getFont(this, id);
    }

    private int color(@ColorRes int id) {
        return ContextCompat.getColor(this, id);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Étiquette d'un SMS : fond, couleur et icône selon sa nature. */
    private TextView badgeView(JournalBadges.Badge badge) {
        TextView view = new TextView(this);
        view.setText(badge.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTypeface(font(R.font.plex_sans_semibold));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        @ColorRes int ink;
        @ColorRes int fill;
        @DrawableRes int icon = 0;
        switch (badge.kind) {
            case SENT:
                ink = R.color.pine;
                fill = R.color.pine_soft;
                icon = R.drawable.ic_badge_check;
                break;
            case OPEN:
                ink = R.color.amber_ink;
                fill = R.color.amber_soft;
                icon = R.drawable.ic_badge_clock;
                break;
            case FAILED:
                ink = R.color.red_ink;
                fill = R.color.red_soft;
                icon = R.drawable.ic_badge_close;
                break;
            case SYNC:
                ink = R.color.blue_ink;
                fill = R.color.blue_soft;
                break;
            default:
                ink = R.color.ink_2;
                fill = R.color.line_soft;
                break;
        }
        view.setTextColor(color(ink));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color(fill));
        background.setCornerRadius(dp(6));
        view.setBackground(background);
        if (icon != 0) {
            Drawable drawable = ContextCompat.getDrawable(this, icon);
            if (drawable != null) {
                drawable = drawable.mutate();
                drawable.setTint(color(ink));
                view.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null);
                view.setCompoundDrawablePadding(dp(4));
            }
        }
        return view;
    }

    private class EntryAdapter extends BaseAdapter {
        private final TimeZone zone = TimeZone.getDefault();

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public JournalEntry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return entries.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_journal, parent, false);
            JournalEntry entry = getItem(position);
            TextView day = view.findViewById(R.id.journal_day);
            boolean firstOfDay = position == 0
                    || !JournalFormat.sameDay(getItem(position - 1).receivedAt, entry.receivedAt, zone);
            day.setVisibility(firstOfDay ? View.VISIBLE : View.GONE);
            if (firstOfDay) day.setText(JournalFormat.day(entry.receivedAt, System.currentTimeMillis(), zone));

            view.findViewById(R.id.journal_card).setBackgroundResource(JournalBadges.hasOpen(entry)
                    ? R.drawable.bg_journal_card_open : R.drawable.bg_journal_card);
            ((TextView) view.findViewById(R.id.journal_sender)).setText(entry.sender);
            ((TextView) view.findViewById(R.id.journal_time)).setText(JournalFormat.time(entry.receivedAt, zone));
            TextView body = view.findViewById(R.id.journal_body);
            body.setText(JournalFormat.excerpt(entry.body));
            body.setMaxLines(3);

            ChipGroup badges = view.findViewById(R.id.journal_badges);
            badges.removeAllViews();
            for (JournalBadges.Badge badge : JournalBadges.of(entry)) badges.addView(badgeView(badge));

            return view;
        }
    }
}
