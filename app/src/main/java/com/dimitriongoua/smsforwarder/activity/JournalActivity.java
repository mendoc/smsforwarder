package com.dimitriongoua.smsforwarder.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.journal.JournalEntry;
import com.dimitriongoua.smsforwarder.journal.JournalFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Journal des SMS traités ces 30 derniers jours, du plus récent au plus ancien, chargé
 * page par page en fin de liste. Toucher une entrée affiche le SMS en entier.
 */
public class JournalActivity extends AppCompatActivity {
    private static final int PAGE_SIZE = 30;

    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<JournalEntry> entries = new ArrayList<>();
    private final Set<Long> expanded = new HashSet<>();
    private EntryAdapter adapter;
    private TextView empty;
    private boolean loading;
    private boolean hasMore = true;
    // Incrémenté à chaque rechargement : une page d'une ancienne liste est ignorée.
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);
        setTitle(R.string.journal_title);
        empty = findViewById(R.id.journal_empty);
        ListView list = findViewById(R.id.journal_list);
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            long entryId = entries.get(position).id;
            if (!expanded.remove(entryId)) expanded.add(entryId);
            adapter.notifyDataSetChanged();
        });
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
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reader.shutdownNow();
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
        final JournalEntry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        reader.execute(() -> {
            JournalDb journal = JournalDb.get(this);
            if (last == null) journal.purge(System.currentTimeMillis());
            List<JournalEntry> page = journal.page(last, PAGE_SIZE);
            main.post(() -> {
                if (current != generation || isFinishing()) return;
                entries.addAll(page);
                hasMore = page.size() == PAGE_SIZE;
                loading = false;
                empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.notifyDataSetChanged();
            });
        });
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
            boolean open = expanded.contains(entry.id);

            String header = JournalFormat.dateTime(entry.receivedAt, zone) + " · " + entry.sender + " · " + entry.simLabel;
            if (Delivery.VIA_SYNC.equals(entry.source)) header += " · synchro";
            ((TextView) view.findViewById(R.id.journal_header)).setText(header);
            ((TextView) view.findViewById(R.id.journal_body)).setText(open ? entry.body : JournalFormat.excerpt(entry.body));

            StringBuilder deliveries = new StringBuilder();
            for (Delivery delivery : entry.deliveries) {
                if (deliveries.length() > 0) deliveries.append('\n');
                deliveries.append(JournalFormat.delivery(delivery, zone));
            }
            if (deliveries.length() == 0) deliveries.append(getString(R.string.journal_no_destination));
            ((TextView) view.findViewById(R.id.journal_deliveries)).setText(deliveries);
            return view;
        }
    }
}
