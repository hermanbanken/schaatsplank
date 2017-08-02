//
//  Types.swift
//  Schaatsplank
//
//  Created by Herman Banken on 02/08/2017.
//  Copyright © 2017 q42. All rights reserved.
//

import Foundation
import CoreMotion

enum AppError: Error {
  case factorWrong // "Factor must always be between 0 and 1"
}

struct Stability: RawRepresentable {
  typealias RawValue = Double
  let rawValue: Double
}

struct Frequency {
  let f: Double
  let time: Date?
}

extension Frequency {
  init(_ freq: Double) {
    self.init(f: freq, time: nil)
  }
}

struct Gravity: RawRepresentable {
  typealias RawValue = CMAcceleration
  let rawValue: CMAcceleration

  var factor: Double { return gravityFunction(zAxis: rawValue.z) }
  var tilt: Double { return atan2(rawValue.z, rawValue.y) }
  var tiltAngle: Double { return tilt * 180 / .pi }
}

struct AccelerationRange {
  let min: Double
  let max: Double
  func forFactor(factor: Double) throws -> Double {
    if(factor > 1 || factor < 0) {
      throw AppError.factorWrong
    }
    return min + (max - min) * factor
  }
}

struct State {
  let speed: Double
  let distance: Double
  let time: Date
  let relTime: Double
  let kind: WhereKind

  func then(acceleration acc: CMAcceleration, time: Date) -> State {
    let dt = time.timeIntervalSince(self.time)
    let ds = acc.x * dt
    let slowDown = pow(0.5, dt)
    let ns = speed * slowDown + ds
    let nd = kind == .midway ? distance + speed * dt : 0
    if speed > 0 && ns < 0 {
      // l <-- r
      return State(speed: ns, distance: nd, time: time, relTime: relTime + dt, kind: .right)
    } else if speed < 0 && ns > 0 {
      // l --> r
      return State(speed: ns, distance: nd, time: time, relTime: relTime + dt, kind: .left)
    } else {
      return State(speed: ns, distance: nd, time: time, relTime: relTime + dt, kind: .midway)
    }
  }
  static func initial() -> State {
    return State(speed: 0, distance: 0, time: Date(), relTime: 0, kind: .midway)
  }
}

struct ExternalState {
  var distance: Double
  let time: Double
  let speed: Double
  let measures: (Gravity,Stability,Frequency)?

  static func initial() -> ExternalState {
    return ExternalState(distance: 0, time: 0, speed: 0, measures: nil)
  }
}

enum WhereKind {
  case left
  case midway
  case right
}
