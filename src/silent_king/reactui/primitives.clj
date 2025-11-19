(ns silent-king.reactui.primitives
  "Load all Reactified UI primitive namespaces so their multimethods are registered."
  (:require [silent-king.reactui.primitives.bar-chart]
            [silent-king.reactui.primitives.button]
            [silent-king.reactui.primitives.dropdown]
            [silent-king.reactui.primitives.label]
            [silent-king.reactui.primitives.minimap]
            [silent-king.reactui.primitives.slider]
            [silent-king.reactui.primitives.stack]
            [silent-king.reactui.primitives.window]))

(set! *warn-on-reflection* true)
