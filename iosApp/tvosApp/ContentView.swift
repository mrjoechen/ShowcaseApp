import UIKit
import SwiftUI
import ComposeApp

// tvOS has no status bar. Let Compose own the controller and remote input.
struct ComposeAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeAppView()
            .ignoresSafeArea()
    }
}
