//
//  Observable+Extensions.swift
//  Schaatsplank
//
//  Created by Herman Banken on 02/08/2017.
//  Copyright © 2017 q42. All rights reserved.
//

import Foundation
import RxSwift

extension ObservableType {

  func sliding<R>(ops: @escaping (Self.E, Self.E) -> R) -> Observable<R> {
    return Observable<R>.create { sub in
      var last: Self.E?
      return self.subscribe(
        onNext: { next in
          if let l = last {
            sub.onNext(ops(l, next))
          }
          last = next
      },
        onError: { e in sub.onError(e) },
        onCompleted: { sub.onCompleted() })
    }
  }
}
