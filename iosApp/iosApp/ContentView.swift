import UIKit
import SwiftUI
import ComposeApp

struct ComposeAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        if #available(iOS 14.0, *) {
            ComposeAppView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler iOS 14.0
                .edgesIgnoringSafeArea(.all)
        } else {
            // Fallback on earlier versions
            ComposeAppView()
                .edgesIgnoringSafeArea(.all)
        }
    }
}



