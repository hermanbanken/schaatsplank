//
//  Algorithm.swift
//  Schaatsplank
//
//  Created by Herman Banken on 02/08/2017.
//  Copyright © 2017 q42. All rights reserved.
//

// MARK: Algorithm

import Foundation
import RxSwift
import CoreMotion

func stateEvents(motion: Observable<CMDeviceMotion>) -> Observable<(State,Gravity)> {
  let gravity: Observable<Gravity> = motion.map { Gravity(rawValue: $0.gravity) }
  let states: Observable<State> = motion
    .map { $0.userAcceleration }.scan(State.initial(), accumulator: { $0.then(acceleration: $1, time: Date()) })
    .multicast(PublishSubject.init) { states in
      return Observable.merge([
        states.filter { $0.kind != .midway }.throttle(0.500, latest: false, scheduler: MainScheduler.asyncInstance),
        states.filter { $0.kind == .midway }.throttle(0.050, latest: false, scheduler: MainScheduler.asyncInstance)
        ])
  }

  return states.withLatestFrom(gravity) { $0 }
}

func run(motion: Observable<CMDeviceMotion>) -> Observable<ExternalState> {
  return match(obs: stateEvents(motion: motion))
}

func match(obs: Observable<(State,Gravity)>) -> Observable<ExternalState> {
  let frequency = obs
    .map { $0.0 }
    .filter { $0.kind != .midway }
    .scan((2.0, State.initial())) { previous, state -> (Double, State) in
      let dt = state.relTime - previous.1.relTime
      let next = state.relTime != 0 ? (previous.1.kind != state.kind ? dt : dt / 2) : 0
      return (next, state)
    }
    .map { $0.0 == 0 ? Frequency(0) : Frequency(1.0 / $0.0) }
    .filter { (freq: Frequency) in freq.f != 0.0 }

  let freqStability: Observable<Stability> = frequency.sliding {
      // freq should be between 1Hz and 1/10Hz
      if($0.f < 0.1 || $1.f < 0.1 || $0.f > 1.0 || $1.f > 1.0) {
        return 0.0
      } else {
        return min($0.f / $1.f, $1.f / $0.f)
      }
    }
    .map { Stability(rawValue: $0) }

  let freqStab: Observable<(Stability,Frequency)> = Observable.zip(
    freqStability.startWith(Stability(rawValue: 0.0)),
    frequency
  )

  return obs
    .withLatestFrom(freqStab) { a, b in (a.0, (a.1, b.0, b.1)) }
    .scan(ExternalState.initial(), accumulator: scan)
//    .multicast(PublishSubject.init) { Observable.from([
//        // take until distance + 1
//        $0.takeWhile { (state: ExternalState) -> Bool in state.distance < 400.0 },
//        $0.take(1)
////          .map { (it: ExternalState) -> ExternalState in
////          var copy = it
////          copy.distance = 400
////          return copy
////        }
//      ]).concat()
//    }

}

func scan(prev: ExternalState, next: (state: State, measures: (shape: Gravity, stability: Stability, freq: Frequency))) throws -> ExternalState {
  let (shape, stability, freq) = next.measures
  let dt = (next.state.relTime - prev.time)

  // calculate speed = prevSpeed + stabilityFactor * gravityFactor
  let a: Double = (frequencyFunction(next.measures.freq.f * 2) * next.measures.shape.rawValue.z * 0.5)
  let b: Double = (next.measures.stability.rawValue * 0.5)
  let goodness: Double
  if next.measures.freq.f == 0 && next.measures.stability.rawValue == 0 {
    goodness = 0.0
  } else if freq.time == nil {
    goodness = min(1.0, max(0.0, a + b + 0.1))
  } else if let ftime = freq.time, next.state.relTime - ftime < 1.0 {
    goodness = min(1.0, max(0.0, a + b + 0.1))
  } else {
    goodness = 0.0
  }

  let range = accelerationRange(prev.speed)
  let acc = try range.forFactor(factor: goodness)
  let speed = prev.speed + acc * dt
  let distance = prev.distance + speed * dt

  return ExternalState(distance: distance, time: next.state.relTime, speed: speed, measures: next.measures)
}

typealias Output = String


func frequencyFunction(_ f: Double) -> Double {
  let a = -pow(f / 2 - 0.4, 4.0) + 1
  let b = -pow(f / 3.2 - 1.3, 2.0) + 0.6
  return (a + b)
}


func gravityFunction(zAxis: Double) -> Double {
  let quad = -pow(zAxis / 3.0 - 1, 4.0) / 7
  let duo = pow(zAxis / 3.0 - 1, 2.0) / 2
  let sin = zAxis / 4
  return (quad + duo + sin) / 4.6 + 0.5
}

func accelerationRange(_ speed: Double) -> AccelerationRange {
  let maxSpeed = 25.0
  let maxAcc = 4.0
  let half = maxSpeed / maxAcc
  if(speed < maxSpeed) {
    return AccelerationRange(min: -speed / half, max: maxAcc - speed / half)
  } else {
    return AccelerationRange(min: -maxSpeed / half, max: 0.0)
  }
}
