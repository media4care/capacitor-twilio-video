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

        NotificationCenter.default.addObserver(self, selector: #selector(self.sendTwilioEvent), name: Notification.Name("CALL_EVENTS"), object: nil)

        DispatchQueue.main.sync {
            let viewController = TwilioVideoViewController(nibName: "TwilioVideoViewController", bundle: bundle)
            viewController.roomName = roomName
            viewController.accessToken = accessToken
            
            if (options != nil) {
                if let autoHideControls = options!["autoHideControls"] as? Bool {
                    viewController.autoHideControls = autoHideControls
                }

                if let showSwitchCamera = options!["showSwitchCamera"] as? Bool {
                    viewController.showSwitchCamera = showSwitchCamera
                }

                if let showMute = options!["showMute"] as? Bool {
                    viewController.showMute = showMute
                }

                if let buttonSize = options!["buttonSize"] as? String {
                    viewController.buttonSize = buttonSize
                }

                if let connectingText = options!["connectingText"] as? String {
                    viewController.connectingText = connectingText
                }

                if let reconnectingText = options!["reconnectingText"] as? String {
                    viewController.reconnectingText = reconnectingText
                }

                if let videoQuality = options!["videoQuality"] as? String {
                    viewController.videoQuality = videoQuality
                }

                // TODO: add options for controlsPosition
            }
            
            viewController.modalPresentationStyle = UIModalPresentationStyle.fullScreen
            self.bridge?.viewController?.present(viewController, animated: false, completion: nil)
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
            self.bridge?.viewController?.dismiss(animated: false, completion: nil)
        }
    }
}
