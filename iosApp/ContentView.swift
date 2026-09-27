import SwiftUI
import shared

struct DeviceRow: Identifiable {
    let id = UUID()
    let title: String
    let mac: String
    let active: String?
    let key: String
}

@MainActor
class TokenViewModel: ObservableObject {
    @Published var method: LoginMethod = .amazfit
    @Published var username = ""
    @Published var password = ""
    @Published var busy = false
    @Published var devices: [DeviceRow] = []
    @Published var errorMessage: String? = nil
    @Published var showError = false

    func lookup() {
        guard !username.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty else {
            errorMessage = "Please fill in both e-mail and password."
            showError = true
            return
        }
        busy = true
        devices = []
        let repo = TokenRepository(engine: HttpEngine())
        let method = self.method
        let username = self.username.trimmingCharacters(in: .whitespaces)
        let password = self.password
        repo.fetchDevices(method: method, username: username, password: password) { list, error in
            DispatchQueue.main.async {
                self.busy = false
                if let error = error {
                    self.errorMessage = "Lookup failed: \(error.message ?? error.localizedDescription)"
                    self.showError = true
                } else if let list = list {
                    self.devices = list.map {
                        DeviceRow(title: $0.title, mac: $0.mac, active: $0.active, key: $0.key)
                    }
                    if list.isEmpty {
                        self.errorMessage = "Login worked, but no bound devices were found."
                        self.showError = true
                    }
                }
            }
        }
    }
}

struct ContentView: View {
    @StateObject private var vm = TokenViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Why this app?")
                        .font(.headline)
                    Text("Newer Amazfit, Zepp and Xiaomi watches need their unique Bluetooth pairing key to work with Gadgetbridge. The key is stored on Huami/Xiaomi servers next to your paired devices — this app logs in and shows it so you can paste it into Gadgetbridge.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Picker("Method", selection: $vm.method) {
                        Text("Amazfit / Zepp").tag(LoginMethod.amazfit)
                        Text("Xiaomi").tag(LoginMethod.xiaomi)
                    }
                    .pickerStyle(.segmented)

                    TextField("Account e-mail (username)", text: $vm.username)
                        .textFieldStyle(.roundedBorder)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $vm.password)
                        .textFieldStyle(.roundedBorder)
                        .textContentType(.password)

                    Button(action: { vm.lookup() }) {
                        if vm.busy {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Get Bluetooth keys")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(red: 0.76, green: 0.25, blue: 0.05))
                    .disabled(vm.busy)

                    if !vm.devices.isEmpty {
                        Text("Paired devices (\(vm.devices.count))").font(.headline)
                        ForEach(vm.devices) { d in
                            VStack(alignment: .leading, spacing: 3) {
                                Text(d.title).fontWeight(.semibold)
                                Text("MAC: \(d.mac)").font(.system(.body, design: .monospaced))
                                if let active = d.active {
                                    Text("Active: \(active)")
                                }
                                Text("Key: \(d.key)").font(.system(.body, design: .monospaced))
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(Color(.secondarySystemBackground))
                            .cornerRadius(10)
                        }
                    }
                }
                .padding(20)
            }
            .navigationTitle("huami-token")
            .alert("Error", isPresented: $vm.showError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(vm.errorMessage ?? "Unknown error")
            }
        }
    }
}
