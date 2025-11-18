(ns silent-king.ui.specs
  "Specs and factory functions for UI data structures.

  This namespace defines the data contracts for all UI components and provides
  factory functions that ensure all required properties are initialized with
  proper defaults. This eliminates the need for scattered fallback logic
  throughout the rendering code."
  (:require [clojure.spec.alpha :as s]
            [silent-king.ui.theme :as theme]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Star Inspector Specs
;; =============================================================================

(s/def ::visible? boolean?)
(s/def ::panel-width number?)
(s/def ::panel-height number?)
(s/def ::margin number?)
(s/def ::current-x number?)
(s/def ::target-x number?)
(s/def ::slide-speed number?)
(s/def ::panel-entity (s/nilable int?))

(s/def ::star-inspector-state
  (s/keys :req-un [::visible?
                   ::panel-width
                   ::panel-height
                   ::margin
                   ::current-x
                   ::target-x
                   ::slide-speed
                   ::panel-entity]))

;; =============================================================================
;; Hyperlane Settings Panel Specs
;; =============================================================================

(s/def ::expanded? boolean?)
(s/def ::target number?)
(s/def ::progress number?)
(s/def ::collapsed-height number?)
(s/def ::expanded-height number?)
(s/def ::animation-speed number?)
(s/def ::body-entities (s/coll-of int? :kind vector?))
(s/def ::body-visible? boolean?)
(s/def ::last-update number?)
(s/def ::header-entity (s/nilable int?))
(s/def ::last-settings (s/nilable map?))

(s/def ::hyperlane-settings-state
  (s/keys :req-un [::expanded?
                   ::target
                   ::progress
                   ::collapsed-height
                   ::expanded-height
                   ::animation-speed
                   ::panel-entity
                   ::header-entity
                   ::body-entities
                   ::body-visible?
                   ::last-update
                   ::last-settings]))

;; =============================================================================
;; Performance Dashboard Specs
;; =============================================================================

(s/def ::pinned? boolean?)
(s/def ::history-limit int?)
(s/def ::body-entity (s/nilable int?))
(s/def ::position (s/nilable (s/keys :req-un [::x ::y])))
(s/def ::x number?)
(s/def ::y number?)

(s/def ::performance-dashboard-state
  (s/keys :req-un [::expanded?
                   ::pinned?
                   ::collapsed-height
                   ::expanded-height
                   ::history-limit
                   ::panel-entity
                   ::header-entity
                   ::body-entity
                   ::position]))

;; =============================================================================
;; Factory Functions
;; =============================================================================

(defn make-star-inspector-state
  "Create a star inspector state with all required properties initialized.

  Optional overrides can be provided as a map."
  ([]
   (make-star-inspector-state {}))
  ([overrides]
   (let [defaults {:visible? false
                   :panel-width (theme/get-panel-dimension :inspector :width)
                   :panel-height (theme/get-panel-dimension :inspector :height)
                   :margin (theme/get-panel-dimension :inspector :margin)
                   :current-x 0.0
                   :target-x 0.0
                   :slide-speed (:slide-speed theme/animation)
                   :panel-entity nil}
         state (merge defaults overrides)]
     (when-not (s/valid? ::star-inspector-state state)
       (throw (ex-info "Invalid star inspector state"
                       {:explain (s/explain-str ::star-inspector-state state)})))
     state)))

(defn make-hyperlane-settings-state
  "Create a hyperlane settings panel state with all required properties initialized.

  Optional overrides can be provided as a map."
  ([]
   (make-hyperlane-settings-state {}))
  ([overrides]
   (let [defaults {:expanded? false
                   :target 0.0
                   :progress 0.0
                   :collapsed-height (theme/get-panel-dimension :settings :collapsed)
                   :expanded-height (theme/get-panel-dimension :settings :expanded)
                   :animation-speed (:fade-speed theme/animation)
                   :panel-entity nil
                   :header-entity nil
                   :body-entities []
                   :body-visible? false
                   :last-update 0.0
                   :last-settings nil}
         state (merge defaults overrides)]
     (when-not (s/valid? ::hyperlane-settings-state state)
       (throw (ex-info "Invalid hyperlane settings state"
                       {:explain (s/explain-str ::hyperlane-settings-state state)})))
     state)))

(defn make-performance-dashboard-state
  "Create a performance dashboard state with all required properties initialized.

  Optional overrides can be provided as a map."
  ([]
   (make-performance-dashboard-state {}))
  ([overrides]
   (let [defaults {:expanded? false
                   :pinned? false
                   :collapsed-height (theme/get-panel-dimension :dashboard :collapsed)
                   :expanded-height (theme/get-panel-dimension :dashboard :expanded)
                   :history-limit (:history-limit theme/animation)
                   :panel-entity nil
                   :header-entity nil
                   :body-entity nil
                   :position nil}
         state (merge defaults overrides)]
     (when-not (s/valid? ::performance-dashboard-state state)
       (throw (ex-info "Invalid performance dashboard state"
                       {:explain (s/explain-str ::performance-dashboard-state state)})))
     state)))

;; =============================================================================
;; Validation Helpers
;; =============================================================================

(defn validate-star-inspector-state!
  "Validate a star inspector state, throwing an exception if invalid.
  Useful for development/debugging."
  [state]
  (when-not (s/valid? ::star-inspector-state state)
    (throw (ex-info "Invalid star inspector state"
                    {:explain (s/explain-str ::star-inspector-state state)
                     :state state}))))

(defn validate-hyperlane-settings-state!
  "Validate a hyperlane settings state, throwing an exception if invalid.
  Useful for development/debugging."
  [state]
  (when-not (s/valid? ::hyperlane-settings-state state)
    (throw (ex-info "Invalid hyperlane settings state"
                    {:explain (s/explain-str ::hyperlane-settings-state state)
                     :state state}))))

(defn validate-performance-dashboard-state!
  "Validate a performance dashboard state, throwing an exception if invalid.
  Useful for development/debugging."
  [state]
  (when-not (s/valid? ::performance-dashboard-state state)
    (throw (ex-info "Invalid performance dashboard state"
                    {:explain (s/explain-str ::performance-dashboard-state state)
                     :state state}))))
