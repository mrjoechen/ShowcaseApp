import UIKit
import SwiftUI
import ComposeApp

private let statusBarVisibilityNotificationName = Notification.Name("ShowcaseStatusBarVisibilityDidChange")
private let statusBarHiddenUserInfoKey = "hidden"

final class StatusBarAwareComposeViewController: UIViewController {
    private let composeViewController: UIViewController
    private var statusBarHidden = false {
        didSet {
            guard oldValue != statusBarHidden else { return }
            UIView.animate(withDuration: 0.2) { [weak self] in
                self?.setNeedsStatusBarAppearanceUpdate()
            }
        }
    }
    private var statusBarObserver: NSObjectProtocol?

    init(composeViewController: UIViewController) {
        self.composeViewController = composeViewController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(composeViewController)
        composeViewController.view.frame = view.bounds
        composeViewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(composeViewController.view)
        composeViewController.didMove(toParent: self)

        statusBarObserver = NotificationCenter.default.addObserver(
            forName: statusBarVisibilityNotificationName,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard let self else { return }
            let hiddenValue = notification.userInfo?[statusBarHiddenUserInfoKey]
            if let hidden = hiddenValue as? Bool {
                self.statusBarHidden = hidden
            } else if let hiddenNumber = hiddenValue as? NSNumber {
                self.statusBarHidden = hiddenNumber.boolValue
            }
        }
    }

    deinit {
        if let observer = statusBarObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    override var prefersStatusBarHidden: Bool {
        statusBarHidden
    }

    override var childForStatusBarStyle: UIViewController? {
        composeViewController
    }

    override var childForStatusBarHidden: UIViewController? {
        nil
    }
}

struct ComposeAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        let composeViewController = MainViewControllerKt.MainViewController()
        return StatusBarAwareComposeViewController(composeViewController: composeViewController)
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


