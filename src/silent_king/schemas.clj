(ns silent-king.schemas
  "Malli schemas and boundary validation helpers for world and UI state."
  (:require [malli.core :as m]
            [malli.error :as me]))

(set! *warn-on-reflection* true)

(def ^:dynamic *validate-boundaries?*
  "When true, boundary helpers will enforce schemas and throw on invalid data."
  true)

(defmacro with-boundary-validation
  "Enable boundary validation for the duration of body."
  [& body]
  `(binding [*validate-boundaries?* true]
     ~@body))

(defn assert-valid!
  "Validate value against schema or throw ex-info with humanized errors."
  [schema value context]
  (when-not (m/validate schema value)
    (let [explained (m/explain schema value)]
      (throw (ex-info (str "Invalid " context)
                      {:context context
                       :schema schema
                       :value value
                       :errors explained
                       :humanized (me/humanize explained)})))))

(defn validate-if-enabled!
  "Validate only when *validate-boundaries?* is true."
  [schema value context]
  (when *validate-boundaries?*
    (assert-valid! schema value context)))

;; =============================================================================
;; Scalar helpers
;; =============================================================================

(def NonNegNumber [:and number? [:>= 0]])
(def PositiveId [:and int? [:> 0]])
(def MaybeId [:maybe PositiveId])
(def WorldCoord [:map [:x number?] [:y number?]])

;; =============================================================================
;; Core world model
;; =============================================================================

(def Star
  [:map {:closed false}
   [:id PositiveId]
   [:x number?]
   [:y number?]
   [:size NonNegNumber]
   [:density NonNegNumber]
   [:sprite-path [:or string? keyword?]]
   [:rotation-speed number?]])

(def Planet
  [:map {:closed false}
   [:id PositiveId]
   [:star-id PositiveId]
   [:radius NonNegNumber]
   [:orbital-period NonNegNumber]
   [:phase number?]
   [:size NonNegNumber]
   [:sprite-path [:or string? keyword?]]
   [:eccentricity {:optional true} NonNegNumber]
   [:inclination {:optional true} number?]])

(def HyperlaneColor
  [:map {:closed false}
   [:h number?]
   [:s number?]
   [:v number?]
   [:a {:optional true} number?]])

(def Hyperlane
  [:map {:closed false}
   [:id PositiveId]
   [:from-id PositiveId]
   [:to-id PositiveId]
   [:base-width NonNegNumber]
   [:color-start HyperlaneColor]
   [:color-end HyperlaneColor]
   [:glow-color HyperlaneColor]
   [:animation-offset number?]])

(def Neighbor
  [:map
   [:neighbor-id PositiveId]
   [:hyperlane Hyperlane]])

(def NeighborsByStarId
  [:map-of PositiveId [:sequential Neighbor]])

(def BBox
  [:map
   [:min-x number?]
   [:min-y number?]
   [:max-x number?]
   [:max-y number?]])

(def VoronoiCell
  [:map {:closed false}
   [:star-id PositiveId]
   [:vertices [:sequential WorldCoord]]
   [:bbox BBox]
   [:centroid WorldCoord]
   [:on-envelope? {:optional true} boolean?]
   [:star {:optional true} Star]
   [:relaxed? {:optional true} boolean?]
   [:relaxed-site {:optional true} WorldCoord]])

(def RegionId keyword?)

(def Sector
  [:map {:closed false}
   [:id keyword?]
   [:name string?]
   [:color any?]
   [:star-ids [:set PositiveId]]
   [:center WorldCoord]
   [:capital-id PositiveId]])

(def Region
  [:map {:closed false}
   [:id RegionId]
   [:name string?]
   [:color any?]
   [:star-ids [:set PositiveId]]
   [:center WorldCoord]
   [:sectors [:map-of keyword? Sector]]])

;; =============================================================================
;; Runtime state
;; =============================================================================

(def Camera
  [:map {:closed false}
   [:zoom number?]
   [:pan-x number?]
   [:pan-y number?]])

(def Input
  [:map {:closed false}
   [:mouse-x number?]
   [:mouse-y number?]
   [:mouse-down-x number?]
   [:mouse-down-y number?]
   [:dragging boolean?]
   [:ui-active? boolean?]
   [:mouse-initialized? boolean?]])

(def Time
  [:map {:closed false}
   [:start-time number?]
   [:current-time number?]
   [:frame-count int?]])

(def Assets [:map {:closed false}])
(def Widgets [:map {:closed false} [:layout-dirty [:set any?]]])

(def Selection
  [:map {:closed false}
   [:star-id MaybeId]
   [:last-world-click {:optional true} [:maybe WorldCoord]]
   [:details {:optional true} any?]])

(def UI [:map {:closed false} [:scale number?]])

(def Features
  [:map {:closed false}
   [:stars-and-planets? {:optional true} boolean?]
   [:hyperlanes? {:optional true} boolean?]
   [:voronoi? {:optional true} boolean?]
   [:minimap? {:optional true} boolean?]])

(def HyperlaneSettings
  [:map {:closed false}
   [:enabled? {:optional true} boolean?]
   [:opacity {:optional true} number?]
   [:color-scheme {:optional true} keyword?]
   [:animation? {:optional true} boolean?]
   [:animation-speed {:optional true} number?]
   [:line-width {:optional true} number?]])

(def VoronoiSettings
  [:map {:closed false}
   [:enabled? {:optional true} boolean?]
   [:opacity {:optional true} number?]
   [:line-width {:optional true} number?]
   [:color-scheme {:optional true} keyword?]
   [:show-centroids? {:optional true} boolean?]
   [:hide-border-cells? {:optional true} boolean?]
   [:relax-iterations {:optional true} int?]
   [:relax-step {:optional true} number?]
   [:relax-max-displacement {:optional true} number?]
   [:relax-clip-to-envelope? {:optional true} boolean?]])

(def PerformanceMetrics
  [:map {:closed false}
   [:fps-history {:optional true} [:sequential number?]]
   [:frame-time-history {:optional true} [:sequential number?]]
   [:last-sample-time {:optional true} number?]
   [:latest {:optional true} map?]])

(def Metrics
  [:map {:closed false}
   [:performance PerformanceMetrics]])

(def WorldSnapshot
  [:map {:closed false}
   [:stars [:map-of PositiveId Star]]
   [:planets [:map-of PositiveId Planet]]
   [:hyperlanes [:sequential Hyperlane]]
   [:voronoi-cells {:optional true} [:map-of PositiveId VoronoiCell]]
   [:regions {:optional true} [:map-of keyword? Region]]
   [:neighbors-by-star-id NeighborsByStarId]
   [:next-star-id int?]
   [:next-planet-id int?]
   [:next-hyperlane-id int?]
   [:voronoi-generated? {:optional true} boolean?]])

(def GameState
  [:map {:closed false}
   [:stars [:map-of PositiveId Star]]
   [:planets [:map-of PositiveId Planet]]
   [:hyperlanes [:sequential Hyperlane]]
   [:voronoi-cells [:map-of PositiveId VoronoiCell]]
   [:regions [:map-of keyword? Region]]
   [:neighbors-by-star-id NeighborsByStarId]
   [:next-star-id int?]
   [:next-planet-id int?]
   [:next-hyperlane-id int?]
   [:camera Camera]
   [:input Input]
   [:time Time]
   [:assets Assets]
   [:widgets Widgets]
   [:selection Selection]
   [:ui UI]
   [:features Features]
   [:hyperlane-settings HyperlaneSettings]
   [:voronoi-settings VoronoiSettings]
   [:voronoi-generated? boolean?]
   [:metrics Metrics]])

(def RenderState
  [:map {:closed false}
   [:window any?]
   [:context any?]
   [:surface any?]])

;; =============================================================================
;; Generator return shapes
;; =============================================================================

(def GeneratedGalaxy
  [:map
   [:stars [:map-of PositiveId Star]]
   [:planets [:map-of PositiveId Planet]]
   [:next-star-id int?]
   [:next-planet-id int?]])

(def GeneratedHyperlanes
  [:map
   [:hyperlanes [:sequential Hyperlane]]
   [:neighbors-by-star-id NeighborsByStarId]
   [:next-hyperlane-id int?]
   [:elapsed-ms {:optional true} int?]])

;; =============================================================================
;; REPL helpers
;; =============================================================================

(defn check-game-state!
  "Validate a game-state map or atom; returns true on success."
  [game-state]
  (let [value (if (instance? clojure.lang.IAtom game-state) @game-state game-state)]
    (assert-valid! GameState value "game-state")
    true))

(defn explain-game-state
  "Return humanized validation errors for a game-state value."
  [game-state]
  (let [value (if (instance? clojure.lang.IAtom game-state) @game-state game-state)]
    (me/humanize (m/explain GameState value))))
