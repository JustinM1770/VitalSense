#if os(iOS)
// EmergencyContactsView.swift — Agregar y gestionar contactos de emergencia

import SwiftUI

struct EmergencyContactsView: View {
    @StateObject private var service = EmergencyContactsService.shared
    @State private var showAddSheet  = false

    var body: some View {
        ZStack {
            Color(hex: "#F0F2F5").ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack {
                    Text("Contactos de emergencia")
                        .font(.custom("Manrope-Bold", size: 18))
                        .foregroundColor(Color.textNavy)
                    Spacer()
                    Button {
                        showAddSheet = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 26))
                            .foregroundColor(Color.primaryBlue)
                    }
                }
                .padding(.horizontal, Spacing.xl)
                .padding(.top, 16)
                .padding(.bottom, 12)

                if service.contacts.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "person.2.slash")
                            .font(.system(size: 48))
                            .foregroundColor(Color.primaryBlue.opacity(0.4))
                        Text("Sin contactos de emergencia")
                            .font(.custom("Manrope-SemiBold", size: 16))
                            .foregroundColor(Color.textNavy)
                        Text("Agrega familiares, médicos o servicios de emergencia para notificarlos en caso de caída o SOS.")
                            .font(.custom("Manrope-Regular", size: 13))
                            .foregroundColor(Color(hex: "#6B7280"))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    Spacer()
                } else {
                    ScrollView {
                        VStack(spacing: 10) {
                            ForEach(service.contacts) { contact in
                                ContactRow(contact: contact) {
                                    service.deleteContact(id: contact.id)
                                }
                            }
                        }
                        .padding(.horizontal, Spacing.xl)
                        .padding(.top, 8)
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear { service.loadContacts() }
        .sheet(isPresented: $showAddSheet) {
            AddContactSheet { contact in
                service.addContact(contact) { _ in }
            }
        }
    }
}

// MARK: - ContactRow

private struct ContactRow: View {
    let contact: EmergencyContact
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            // Ícono de relación
            ZStack {
                Circle()
                    .fill(relationColor.opacity(0.15))
                    .frame(width: 46, height: 46)
                Image(systemName: relationIcon)
                    .font(.system(size: 20))
                    .foregroundColor(relationColor)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(contact.name)
                    .font(.custom("Manrope-SemiBold", size: 15))
                    .foregroundColor(Color.textNavy)
                Text(contact.phone)
                    .font(.custom("Manrope-Regular", size: 13))
                    .foregroundColor(Color(hex: "#6B7280"))
                Text(contact.relation)
                    .font(.custom("Manrope-Regular", size: 11))
                    .foregroundColor(relationColor)
            }

            Spacer()

            HStack(spacing: 12) {
                if let url = EmergencyContactsService.shared.smsURL(for: contact, sosId: "", lat: 0, lng: 0) {
                    Link(destination: url) {
                        Image(systemName: "message.fill")
                            .foregroundColor(Color.primaryBlue)
                    }
                }
                if let url = EmergencyContactsService.shared.callURL(for: contact) {
                    Link(destination: url) {
                        Image(systemName: "phone.fill")
                            .foregroundColor(Color.successGreen)
                    }
                }
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .foregroundColor(Color(hex: "#EF4444"))
                }
            }
            .font(.system(size: 18))
        }
        .padding(14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.04), radius: 4, y: 2)
    }

    private var relationColor: Color {
        switch contact.relation {
        case "Médico":            return Color.primaryBlue
        case "Protección Civil":  return Color(hex: "#EF4444")
        case "Ambulancia":        return Color(hex: "#F59E0B")
        default:                  return Color.successGreen
        }
    }

    private var relationIcon: String {
        switch contact.relation {
        case "Médico":            return "stethoscope"
        case "Protección Civil":  return "shield.fill"
        case "Ambulancia":        return "cross.fill"
        default:                  return "person.fill"
        }
    }
}

// MARK: - AddContactSheet

struct AddContactSheet: View {
    let onAdd: (EmergencyContact) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var name     = ""
    @State private var phone    = ""
    @State private var relation = "Familiar"

    private let relations = ["Familiar", "Médico", "Protección Civil", "Ambulancia", "Otro"]

    var body: some View {
        NavigationView {
            Form {
                Section("Información del contacto") {
                    TextField("Nombre completo", text: $name)
                    TextField("Teléfono (+52XXXXXXXXXX)", text: $phone)
                        .keyboardType(.phonePad)
                }
                Section("Tipo de contacto") {
                    Picker("Relación", selection: $relation) {
                        ForEach(relations, id: \.self) { Text($0) }
                    }
                    .pickerStyle(.segmented)
                }
            }
            .navigationTitle("Nuevo contacto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Agregar") {
                        let contact = EmergencyContact(
                            id: UUID().uuidString,
                            name: name.trimmingCharacters(in: .whitespaces),
                            phone: phone.trimmingCharacters(in: .whitespaces),
                            relation: relation
                        )
                        onAdd(contact)
                        dismiss()
                    }
                    .disabled(name.isEmpty || phone.isEmpty)
                }
            }
        }
    }
}

#endif
