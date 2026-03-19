package mx.ita.vitalsense.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import mx.ita.vitalsense.data.model.VitalsData

class VitalsRepository {

    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    private fun getVitalsRef() = FirebaseDatabase.getInstance()
        .getReference("vitals/current/${auth.currentUser?.uid ?: "global"}")

    fun observeVitals(): Flow<Result<VitalsData>> = callbackFlow {
        val ref = getVitalsRef()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val vitals = snapshot.getValue(VitalsData::class.java)
                    trySend(Result.success(vitals ?: VitalsData()))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
