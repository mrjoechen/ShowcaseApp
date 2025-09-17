import SwiftUI
import ComposeApp
import Sentry

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
