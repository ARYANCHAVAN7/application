package com.example.application;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HospitalActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration emergencyListener;
    private ListenerRegistration fleetListener;

    private TextView tvHospitalName, tvAvailableBeds, tvAvailableAmbulances, tvActiveEmergencies, tvNoRequests;
    private EditText etTotalBeds, etAvailableBeds, etTotalAmbulances, etAvailableAmbulances;
    private EditText etDriverName, etDriverPhone, etDriverEmail, etDriverPassword, etVehiclePlate, etVehicleType;
    private RecyclerView rvAmbulanceFleet, rvEmergencyRequests;
    private AmbulanceAdapter adapter;
    private EmergencyAdapter emergencyAdapter;
    private List<Map<String, Object>> fleetList = new ArrayList<>();
    private List<Map<String, Object>> emergencyList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hospital);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupRecyclerView();

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(HospitalActivity.this, StartingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnUpdateResources).setOnClickListener(v -> updateResources());
        findViewById(R.id.btnAddAmbulance).setOnClickListener(v -> addAmbulance());
    }

    private void bindViews() {
        tvHospitalName = findViewById(R.id.tvHospitalName);
        tvAvailableBeds = findViewById(R.id.tvAvailableBeds);
        tvAvailableAmbulances = findViewById(R.id.tvAvailableAmbulances);
        tvActiveEmergencies = findViewById(R.id.tvActiveEmergencies);
        tvNoRequests = findViewById(R.id.tvNoRequests);

        etTotalBeds = findViewById(R.id.etTotalBeds);
        etAvailableBeds = findViewById(R.id.etAvailableBeds);
        etTotalAmbulances = findViewById(R.id.etTotalAmbulances);
        etAvailableAmbulances = findViewById(R.id.etAvailableAmbulances);

        etDriverName = findViewById(R.id.etDriverName);
        etDriverPhone = findViewById(R.id.etDriverPhone);
        etDriverEmail = findViewById(R.id.etDriverEmail);
        etDriverPassword = findViewById(R.id.etDriverPassword);
        etVehiclePlate = findViewById(R.id.etVehiclePlate);
        etVehicleType = findViewById(R.id.etVehicleType);
        rvAmbulanceFleet = findViewById(R.id.rvAmbulanceFleet);
        rvEmergencyRequests = findViewById(R.id.rvEmergencyRequests);
    }

    private void setupRecyclerView() {
        adapter = new AmbulanceAdapter(fleetList);
        rvAmbulanceFleet.setLayoutManager(new LinearLayoutManager(this));
        rvAmbulanceFleet.setAdapter(adapter);

        emergencyAdapter = new EmergencyAdapter(emergencyList);
        rvEmergencyRequests.setLayoutManager(new LinearLayoutManager(this));
        rvEmergencyRequests.setAdapter(emergencyAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
        } else {
            loadHospitalData();
            listenForEmergencies();
            listenForFleet();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (emergencyListener != null) emergencyListener.remove();
        if (fleetListener != null) fleetListener.remove();
    }

    private void loadHospitalData() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("hospitalName");
                        tvHospitalName.setText(name);

                        etTotalBeds.setText(documentSnapshot.getString("totalBeds"));
                        etAvailableBeds.setText(documentSnapshot.getString("availableBeds"));
                        etTotalAmbulances.setText(documentSnapshot.getString("totalAmbulances"));
                        etAvailableAmbulances.setText(documentSnapshot.getString("availableAmbulances"));

                        tvAvailableBeds.setText(documentSnapshot.getString("availableBeds"));
                        tvAvailableAmbulances.setText(documentSnapshot.getString("availableAmbulances"));
                    }
                });
    }

    private void updateResources() {
        String totalBedsStr = etTotalBeds.getText().toString().trim();
        String availableBedsStr = etAvailableBeds.getText().toString().trim();
        String totalAmbulancesStr = etTotalAmbulances.getText().toString().trim();
        String availableAmbulancesStr = etAvailableAmbulances.getText().toString().trim();

        if (totalBedsStr.isEmpty() || availableBedsStr.isEmpty() || totalAmbulancesStr.isEmpty() || availableAmbulancesStr.isEmpty()) {
            Toast.makeText(this, "Please fill all resource fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int totalBeds = Integer.parseInt(totalBedsStr);
            int availableBeds = Integer.parseInt(availableBedsStr);
            int totalAmbulances = Integer.parseInt(totalAmbulancesStr);
            int availableAmbulances = Integer.parseInt(availableAmbulancesStr);

            boolean hasError = false;

            if (availableBeds > totalBeds) {
                etAvailableBeds.setError("Available beds cannot exceed total beds");
                hasError = true;
            }
            if (availableAmbulances > totalAmbulances) {
                etAvailableAmbulances.setError("Available ambulances cannot exceed total ambulances");
                hasError = true;
            }
            if (totalBeds < 0 || availableBeds < 0 || totalAmbulances < 0 || availableAmbulances < 0) {
                Toast.makeText(this, "Values cannot be negative", Toast.LENGTH_SHORT).show();
                hasError = true;
            }

            if (hasError) return;

            String uid = mAuth.getCurrentUser().getUid();
            Map<String, Object> updates = new HashMap<>();
            updates.put("totalBeds", totalBedsStr);
            updates.put("availableBeds", availableBedsStr);
            updates.put("totalAmbulances", totalAmbulancesStr);
            updates.put("availableAmbulances", availableAmbulancesStr);

            db.collection("users").document(uid).update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Resources updated successfully", Toast.LENGTH_SHORT).show();
                        tvAvailableBeds.setText(availableBedsStr);
                        tvAvailableAmbulances.setText(availableAmbulancesStr);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
        }
    }

    private void addAmbulance() {
        String uid = mAuth.getCurrentUser().getUid();
        String name = etDriverName.getText().toString();
        String phone = etDriverPhone.getText().toString();
        String security = etDriverPassword.getText().toString(); // Use password field for security number
        String plate = etVehiclePlate.getText().toString();

        if (name.isEmpty() || security.isEmpty()) {
            Toast.makeText(this, "Driver name and security number required", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> ambulance = new HashMap<>();
        ambulance.put("driverName", name);
        ambulance.put("driverPhone", phone);
        ambulance.put("securityNumber", security);
        ambulance.put("vehicleNumber", plate);
        ambulance.put("hospitalId", uid);
        ambulance.put("hospitalName", tvHospitalName.getText().toString());

        db.collection("ambulances").add(ambulance)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Ambulance added to fleet", Toast.LENGTH_SHORT).show();
                    clearAmbulanceFields();
                });
    }

    private void clearAmbulanceFields() {
        etDriverName.setText("");
        etDriverPhone.setText("");
        etDriverEmail.setText("");
        etDriverPassword.setText("");
        etVehiclePlate.setText("");
        etVehicleType.setText("");
    }

    private void listenForEmergencies() {
        emergencyListener = db.collection("emergencies")
                .whereEqualTo("status", "active")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        tvActiveEmergencies.setText(String.valueOf(value.size()));
                        
                        emergencyList.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            Map<String, Object> data = doc.getData();
                            data.put("id", doc.getId()); // Store document ID for acceptance
                            emergencyList.add(data);
                        }
                        emergencyAdapter.notifyDataSetChanged();

                        if (emergencyList.isEmpty()) {
                            tvNoRequests.setVisibility(View.VISIBLE);
                            rvEmergencyRequests.setVisibility(View.GONE);
                            tvNoRequests.setText(R.string.msg_no_requests);
                        } else {
                            tvNoRequests.setVisibility(View.GONE);
                            rvEmergencyRequests.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void listenForFleet() {
        String uid = mAuth.getCurrentUser().getUid();
        fleetListener = db.collection("ambulances")
                .whereEqualTo("hospitalId", uid)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        fleetList.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            fleetList.add(doc.getData());
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, StartingActivity.class));
        finish();
    }

    private class EmergencyAdapter extends RecyclerView.Adapter<EmergencyAdapter.ViewHolder> {
        private List<Map<String, Object>> list;

        EmergencyAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_request, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> data = list.get(position);
            holder.tvPatientName.setText((String) data.get("userName"));
            String location = (String) data.get("location");
            holder.tvLocation.setText(getString(R.string.label_location_prefix) + (location != null ? location : "Unknown"));
            
            holder.btnAccept.setOnClickListener(v -> {
                String requestId = (String) data.get("id");
                acceptEmergency(requestId);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPatientName, tvLocation;
            Button btnAccept;
            ViewHolder(View v) {
                super(v);
                tvPatientName = v.findViewById(R.id.tvPatientName);
                tvLocation = v.findViewById(R.id.tvLocation);
                btnAccept = v.findViewById(R.id.btnAcceptRequest);
            }
        }
    }

    private void acceptEmergency(String requestId) {
        String hospitalUid = mAuth.getCurrentUser().getUid();
        
        db.collection("emergencies").document(requestId)
                .update("status", "accepted", 
                        "hospitalId", hospitalUid,
                        "hospitalName", tvHospitalName.getText().toString())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Emergency Request Accepted!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to accept: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private class AmbulanceAdapter extends RecyclerView.Adapter<AmbulanceAdapter.ViewHolder> {
        private List<Map<String, Object>> list;

        AmbulanceAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> data = list.get(position);
            holder.text1.setText((String) data.get("driverName") + " (" + data.get("vehicleNumber") + ")");
            holder.text2.setText("Phone: " + data.get("driverPhone"));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}
