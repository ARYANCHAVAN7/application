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

import com.example.application.utils.FirestoreValues;
import com.example.application.utils.SurakshaLog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin review of hospitals with approvalStatus == "pending". */
public class AdminApprovalActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private RecyclerView rvPendingHospitals;
    private TextView tvEmptyState;
    private HospitalApprovalAdapter adapter;
    private final List<DocumentSnapshot> pendingList = new ArrayList<>();
    private String adminUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_approval);

        db = FirebaseFirestore.getInstance();
        rvPendingHospitals = findViewById(R.id.rvPendingHospitals);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        adapter = new HospitalApprovalAdapter(pendingList);
        rvPendingHospitals.setLayoutManager(new LinearLayoutManager(this));
        rvPendingHospitals.setAdapter(adapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        adminUid = user.getUid();
        // Confirm admin role from Firestore before loading anything.
        db.collection("users").document(adminUid).get()
                .addOnSuccessListener(doc -> {
                    if (!"admin".equals(doc.getString("role"))) {
                        SurakshaLog.warn(SurakshaLog.AUTH, "non-admin opened admin screen", "uid=" + adminUid);
                        Toast.makeText(this, "Admin access required", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    loadPendingHospitals();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.AUTH, "admin role check", e, "uid=" + adminUid);
                    finish();
                });
    }

    private void loadPendingHospitals() {
        db.collection("users")
                .whereEqualTo("role", "hospital")
                .whereEqualTo("approvalStatus", "pending")
                .get()
                .addOnSuccessListener(snapshot -> {
                    pendingList.clear();
                    pendingList.addAll(snapshot.getDocuments());
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.HOSPITAL, "load pending hospitals", e);
                    Toast.makeText(this, SurakshaLog.userMessage("Could not load hospitals", e), Toast.LENGTH_LONG).show();
                });
    }

    private void updateUI() {
        boolean empty = pendingList.isEmpty();
        tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvPendingHospitals.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void review(DocumentSnapshot doc, boolean approve) {
        String hospitalId = doc.getId();
        Map<String, Object> updates = new HashMap<>();
        updates.put("isVerified", approve);
        updates.put("approvalStatus", approve ? "approved" : "rejected");
        updates.put("reviewedBy", adminUid);
        updates.put("reviewedAt", FieldValue.serverTimestamp());

        db.collection("users").document(hospitalId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    SurakshaLog.info(SurakshaLog.HOSPITAL, approve ? "hospital approved" : "hospital rejected",
                            "hospitalId=" + hospitalId);
                    Toast.makeText(this, approve ? "Hospital approved" : "Hospital rejected", Toast.LENGTH_SHORT).show();
                    for (int i = 0; i < pendingList.size(); i++) {
                        if (pendingList.get(i).getId().equals(hospitalId)) {
                            pendingList.remove(i);
                            break;
                        }
                    }
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    SurakshaLog.error(SurakshaLog.HOSPITAL, "review hospital", e, "hospitalId=" + hospitalId);
                    Toast.makeText(this, SurakshaLog.userMessage("Update failed", e), Toast.LENGTH_LONG).show();
                });
    }

    private class HospitalApprovalAdapter extends RecyclerView.Adapter<HospitalApprovalAdapter.ViewHolder> {
        private final List<DocumentSnapshot> list;

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
            Double lat = FirestoreValues.getDouble(doc, "latitude");
            Double lng = FirestoreValues.getDouble(doc, "longitude");
            holder.tvName.setText(FirestoreValues.getString(doc, "hospitalName"));
            holder.tvLicense.setText("License: " + FirestoreValues.getString(doc, "licenseNumber")
                    + " | Beds: " + FirestoreValues.getString(doc, "availableBeds"));
            holder.tvContact.setText(FirestoreValues.getString(doc, "email") + " | "
                    + FirestoreValues.getString(doc, "phone")
                    + (lat != null && lng != null ? String.format(Locale.US, " | %.4f, %.4f", lat, lng) : " | no location"));
            holder.btnApprove.setOnClickListener(v -> review(doc, true));
            holder.btnReject.setOnClickListener(v -> review(doc, false));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName, tvLicense, tvContact;
            final Button btnApprove, btnReject;

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvHospitalName);
                tvLicense = v.findViewById(R.id.tvLicenseInfo);
                tvContact = v.findViewById(R.id.tvContactInfo);
                btnApprove = v.findViewById(R.id.btnApprove);
                btnReject = v.findViewById(R.id.btnRejectHospital);
            }
        }
    }
}
