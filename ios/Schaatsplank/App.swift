//
//  App.swift
//  Schaatsplank
//
//  Created by Herman Banken on 02/08/2017.
//  Copyright © 2017 q42. All rights reserved.
//

import Foundation
import CoreMotion
import RxSwift

class App {

  static var shared: App {
    return App()
  }

  let cm = CMMotionManager()
  let queue = OperationQueue()

  init() {
    queue.name = "SensorQueue"
  }

  var motion: Observable<CMDeviceMotion> {
    return Observable<CMDeviceMotion>.create { [weak self] (observer) -> Disposable in
      guard let queue = self?.queue else { return Disposables.create() }
      self?.cm.deviceMotionUpdateInterval = 1.0/60.0
      self?.cm.startDeviceMotionUpdates(to: queue) { motion, err in
        if let motion = motion {
          observer.onNext(motion)
        } else if let err = err {
          observer.onError(err)
        }
      }
      return Disposables.create {
        self?.cm.stopDeviceMotionUpdates()
      }
    }
  }

}
