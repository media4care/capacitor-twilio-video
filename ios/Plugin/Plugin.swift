import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(TwilioVideoPlugin)
public class TwilioVideoPlugin: CAPPlugin {

    var viewController: TwilioVideoViewController?

    @objc func joinTwilioRoom(_ call: CAPPluginCall) {

        guard let roomName = call.options["roomName"] as? String else {
            call.reject("roomName is required")
            return
        }

        guard let accessToken = call.options["accessToken"] as? String else {
            call.reject("accessToken is required")
            return
        }

        let podBundle = Bundle(for: TwilioVideoViewController.self)
        let bundleURL = podBundle.url(forResource: "Plugin", withExtension: "bundle")!
        let bundle = Bundle(url: bundleURL)!

        if self.viewController != nil {
            NSLog("Unexpected behaviour: a view already exists")
        }

        self.viewController = TwilioVideoViewController(nibName: "TwilioVideoViewController", bundle: bundle)
        self.viewController!.roomName = roomName
        self.viewController!.accessToken = accessToken
        self.viewController!.modalPresentationStyle = UIModalPresentationStyle.fullScreen

        DispatchQueue.main.sync {
            self.bridge.viewController.present(self.viewController!, animated: false, completion: nil)
        }

        call.resolve()
    }

    @objc func leaveTwilioRoom(_ call: CAPPluginCall) {
        if let viewController = self.viewController {
            viewController.disconnectRoom()
            self.viewController = nil
        }

        DispatchQueue.main.sync {
            self.bridge.viewController.dismiss(animated: false, completion: nil)
        }
    }
}
