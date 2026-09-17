package com.example;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private CommandHistoryManager historyManager;
    private HistoryAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyManager = new CommandHistoryManager(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnClear = findViewById(R.id.btnClearHistory);
        RecyclerView recyclerView = findViewById(R.id.recyclerViewHistory);
        tvEmpty = findViewById(R.id.tvEmptyHistory);

        btnBack.setOnClickListener(v -> finish());

        adapter = new HistoryAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnClear.setOnClickListener(v -> {
            historyManager.clearHistory();
            loadHistory();
        });

        loadHistory();
    }

    private void loadHistory() {
        List<HistoryItem> items = historyManager.getHistory();
        adapter.setItems(items);
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}
