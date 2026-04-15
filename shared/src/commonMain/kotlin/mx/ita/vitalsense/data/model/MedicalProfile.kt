package mx.ita.vitalsense.data.model

import kotlinx.serialization.Serializable

/** Tipos de sangre ABO + Rh */
enum class BloodType(val label: String) {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-");

    companion object {
        fun fromLabel(label: String): BloodType? = entries.find { it.label == label }
    }
}

/** Datos médicos críticos del paciente — se codifican en el QR */
@Serializable
data class MedicalProfile(
    val nombre:            String = "",
    val apellidos:         String = "",
    val curp:              String = "",
    val fechaNacimiento:   String = "",
        val nacimiento:        String = "",
        val genero:            String = "",
    val tipoSangre:        String = "",   // BloodType.label
    val alergias:          String = "",
    val padecimientos:     String = "",
    val medicamentos:      String = "",
    val contactoEmergencia:String = "",
    val telefonoEmergencia:String = "",
) {
    /** JSON compacto que va dentro del QR */
    fun toQrPayload(): String = buildString {
        append("{")
        append("\"n\":\"$nombre $apellidos\",")
        append("\"curp\":\"$curp\",")
        append("\"dob\":\"$fechaNacimiento\",")
        append("\"bt\":\"$tipoSangre\",")
        append("\"alg\":\"$alergias\",")
        append("\"dx\":\"$padecimientos\",")
        append("\"meds\":\"$medicamentos\",")
        append("\"ec\":\"$contactoEmergencia\",")
        append("\"tel\":\"$telefonoEmergencia\"")
        append("}")
    }
}
