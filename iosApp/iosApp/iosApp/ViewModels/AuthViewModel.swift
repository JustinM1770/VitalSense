import SwiftUI
import FirebaseAuth
import Combine

// MARK: - Auth State
enum AuthUiState: Equatable {
    case idle
    case loading
    case success
    case error(String)
}

// MARK: - Auth ViewModel
class AuthViewModel: ObservableObject {
    @Published var state: AuthUiState = .idle

    private let auth = Auth.auth()

    // MARK: - Register with Email
    func registerWithEmail(name: String, email: String, password: String) {
        state = .loading

        auth.createUser(withEmail: email, password: password) { [weak self] result, error in
            guard let self = self else { return }

            if let error = error {
                self.state = .error(error.localizedDescription)
                return
            }

            // Update display name
            let changeRequest = result?.user.createProfileChangeRequest()
            changeRequest?.displayName = name
            changeRequest?.commitChanges { error in
                if let error = error {
                    self.state = .error(error.localizedDescription)
                } else {
                    self.state = .success
                }
            }
        }
    }

    // MARK: - Login with Email
    func loginWithEmail(email: String, password: String) {
        state = .loading

        auth.signIn(withEmail: email, password: password) { [weak self] result, error in
            guard let self = self else { return }

            if let error = error {
                self.state = .error(error.localizedDescription)
            } else {
                self.state = .success
            }
        }
    }

    // MARK: - Sign In with Google
    func signInWithGoogle() {
        // TODO: Implement Google Sign-In with Credential Manager
        // For now, show error
        state = .error("Google Sign-In no implementado aún")
    }

    // MARK: - Clear Error
    func clearError() {
        state = .idle
    }

    // MARK: - Sign Out
    func signOut() throws {
        try auth.signOut()
    }
}
