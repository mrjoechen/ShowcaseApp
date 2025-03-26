import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        SentrySetupKt.initializeSentry()
    }
	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}
