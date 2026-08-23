package com.example.application;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminApprovalActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private RecyclerView rvPendingHospitals;
    private TextView tvEmptyState;
    private HospitalApprovalAdapter adapter;
    private List<DocumentSnapshot> pendingList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_approval);

        db = FirebaseFirestore.getInstance();
        rvPendingHospitals = findViewById(R.id.rvPendingHospitals);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        setupRecyclerView();
        loadPendingHospitals();
    }

    private void setupRecyclerView() {
        adapter = new HospitalApprovalAdapter(pendingList);
        rvPendingHospitals.setLayoutManager(new LinearLayoutManager(this));
        rvPendingHospitals.setAdapter(adapter);
    }

    private void loadPendingHospitals() {
        db.collection("users")
                .whereEqualTo("role", "hospital")
                .whereEqualTo("isVerified", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    pendingList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        pendingList.add(doc);
                    }
                    updateUI();
                });
    }

    private void updateUI() {
        if (pendingList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            rvPendingHospitals.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            rvPendingHospitals.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private void approveHospital(String docId, int position) {
        db.collection("users").document(docId)
                .update("isVerified", true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Hospital Approved!", Toast.LENGTH_SHORT).show();
                    pendingList.remove(position);
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to approve: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private class HospitalApprovalAdapter extends RecyclerView.Adapter<HospitalApprovalAdapter.ViewHolder> {
        private List<DocumentSnapshot> list;

        HospitalApprovalAdapter(List<DocumentSnapshot> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_hospital, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = list.get(position);
            Map<String, Object> data = doc.getData();
            if (data == null) return;

            holder.tvName.setText((String) data.get("hospitalName"));
            holder.tvLicense.setText("License: " + data.get("licenseNumber"));
            holder.tvContact.setText((String) data.get("email") + " | " + data.get("phone"));

            holder.btnApprove.setOnClickListener(v -> approveHospital(doc.getId(), position));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvLicense, tvContact;
            Button btnApprove;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvHospitalName);
                tvLicense = v.findViewById(R.id.tvLicenseInfo);
                tvContact = v.findViewById(R.id.tvContactInfo);
                btnApprove = v.findViewById(R.id.btnApprove);
            }
        }
    }
}
