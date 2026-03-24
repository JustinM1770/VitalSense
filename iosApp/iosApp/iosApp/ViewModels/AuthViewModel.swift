import SwiftUI
import FirebaseAuth
import GoogleSignIn
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
        guard let rootViewController = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first?.windows.first?.rootViewController else {
            state = .error("No se pudo obtener la ventana principal")
            return
        }

        state = .loading

        GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController) { [weak self] result, error in
            guard let self else { return }

            if let error = error {
                self.state = .error(error.localizedDescription)
                return
            }

            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString else {
                self.state = .error("No se obtuvo el token de Google")
                return
            }

            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: user.accessToken.tokenString
            )

            Auth.auth().signIn(with: credential) { [weak self] _, error in
                guard let self else { return }
                if let error = error {
                    self.state = .error(error.localizedDescription)
                } else {
                    self.state = .success
                }
            }
        }
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
