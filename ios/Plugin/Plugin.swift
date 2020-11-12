import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(TwilioVideoPlugin)
public class TwilioVideoPlugin: CAPPlugin {

    var viewController: TwilioVideoViewController?
    var twilioPluginEvent = "twilio-event"

    @objc private func sendTwilioEvent(notification: NSNotification ){
        if let event = notification.userInfo as? [String: String] {
            for(key, eventName) in event {
                self.notifyListeners(self.twilioPluginEvent, data: ["event": eventName, "detail": ""])
            }
        }
    }

    @objc func joinTwilioRoom(_ call: CAPPluginCall) {

        guard let roomName = call.options["roomName"] as? String else {
            call.reject("roomName is required")
            return
        }

        guard let accessToken = call.options["accessToken"] as? String else {
            call.reject("accessToken is required")
            return
        }

        let options = call.getObject("options") as? [String: Any]

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

        if (options != nil) {
            if let autoHideControls = options!["autoHideControls"] as? Bool {
                self.viewController!.autoHideControls = autoHideControls
            }

            if let showSwitchCamera = options!["showSwitchCamera"] as? Bool {
                self.viewController!.showSwitchCamera = showSwitchCamera
            }

            if let showMute = options!["showMute"] as? Bool {
                self.viewController!.showMute = showMute
            }

            if let buttonSize = options!["buttonSize"] as? String {
                self.viewController!.buttonSize = buttonSize
            }

            if let connectingText = options!["connectingText"] as? String {
                self.viewController!.connectingText = connectingText
            }

            if let reconnectingText = options!["reconnectingText"] as? String {
                self.viewController!.reconnectingText = reconnectingText
            }

            // TODO: add options for connection quality, controlsPosition
        }

        NotificationCenter.default.addObserver(self, selector: #selector(self.sendTwilioEvent), name: Notification.Name("CALL_EVENTS"), object: nil)

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

        NotificationCenter.default.removeObserver(self, name: Notification.Name("CALL_EVENTS"), object: nil)

        DispatchQueue.main.sync {
            self.bridge.viewController.dismiss(animated: false, completion: nil)
        }
    }
}
