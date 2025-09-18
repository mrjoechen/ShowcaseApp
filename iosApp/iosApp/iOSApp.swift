import SwiftUI
import ComposeApp
import Sentry
import SMBClient

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
