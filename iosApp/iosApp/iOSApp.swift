import SwiftUI
import ComposeApp
import Sentry
import SMBClient

//@main
//struct iOSApp: App {
//    init() {
//        SentrySetupKt.initializeSentry()
//    }
//	var body: some Scene {
//		WindowGroup {
//			ContentView()
//		}
//	}
//}


// Wrapper for iOS 13 Compat
// https://stackoverflow.com/questions/62935053/use-main-in-xcode-12
@main
struct iOSApp {
    init() {
        SentrySetupKt.initializeSentry()
    }
    static func main() {
        if #available(iOS 14.0, *) {
            ShowcaseApp.main()
        }
        else {
            UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, nil, NSStringFromClass(AppDelegate.self))
        }
    }
}

