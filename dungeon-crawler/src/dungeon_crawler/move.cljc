(ns dungeon-crawler.move
  (:require [dungeon-crawler.utils :as utils]
            [dungeon-crawler.tiles :as tiles]
            #?(:clj  [play-cljc.macros-java :refer [gl math]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]])))

(def ^:const damping 0.1)
(def ^:const max-velocity 4)
(def ^:const deceleration 0.8)
(def ^:const animation-secs 0.2)
(def ^:const directions [:w :nw :n :ne
                         :e :se :s :sw])
(def ^:const velocities [[-1 0] [-1 -1] [0 -1] [1 -1]
                         [1 0] [1 1] [0 1] [-1 1]])

(defn decelerate
  [velocity]
  (let [velocity (* velocity deceleration)]
    (if (< (math abs velocity) damping)
      0
      velocity)))

(defn get-player-velocity
  [game
   {:keys [pressed-keys mouse-button mouse-x mouse-y]}
   {:keys [x y x-velocity y-velocity]}]
  (if mouse-button
    (let [x (- mouse-x (/ (utils/get-width game) 2))
          y (- mouse-y (/ (utils/get-height game) 2))
          x-adjust (if (= y 0)
                     0
                     (* max-velocity (math abs (/ x y))))
          y-adjust (if (= x 0)
                     0
                     (* max-velocity (math abs (/ y x))))]
      [(* (math #?(:clj signum :cljs sign) x)
          (min max-velocity x-adjust))
       (* (math #?(:clj signum :cljs sign) y)
          (min max-velocity y-adjust))])
    [(cond
       (contains? pressed-keys :left)
       (* -1 max-velocity)
       (contains? pressed-keys :right)
       max-velocity
       :else
       x-velocity)
     (cond
       (contains? pressed-keys :up)
       (* -1 max-velocity)
       (contains? pressed-keys :down)
       max-velocity
       :else
       y-velocity)]))

(defn get-direction
  [x-velocity y-velocity]
  (some->> velocities
           (filter (fn [[x y]]
                     (and (= x (int (math #?(:clj signum :cljs sign) (float x-velocity))))
                          (= y (int (math #?(:clj signum :cljs sign) (float y-velocity)))))))
           first
           (.indexOf velocities)
           (nth directions)))

(defn move
  [{:keys [delta-time] :as game} state {:keys [x y] :as character}]
  (let [[x-velocity y-velocity] (get-player-velocity game state character)
        x-change (* x-velocity delta-time)
        y-change (* y-velocity delta-time)]
    (if (or (not= 0 x-change) (not= 0 y-change))
      (assoc character
        :x-velocity (decelerate x-velocity)
        :y-velocity (decelerate y-velocity)
        :x-change x-change
        :y-change y-change
        :x (+ x x-change)
        :y (+ y y-change))
      character)))

(defn prevent-move
  [tiled-map {:keys [x y
                     width height
                     x-change y-change]
              :as character}]
  (let [old-x (- x x-change)
        old-y (- y y-change)]
    (cond-> character
            (tiles/touching-tile? tiled-map "walls" x old-y width height)
            (assoc :x-velocity 0 :x-change 0 :x old-x)
            (tiles/touching-tile? tiled-map "walls" old-x y width height)
            (assoc :y-velocity 0 :y-change 0 :y old-y))))

(defn animate
  [{:keys [total-time]}
   {:keys [x-velocity y-velocity moves current-image]
    :as character}]
  (let [direction (get-direction x-velocity y-velocity)]
    (-> character
        (assoc :current-image
          (if (or (not= x-velocity 0)
                  (not= y-velocity 0))
            (let [images (direction moves)
                  cycle-time (mod total-time (* animation-secs (count images)))]
              (nth images (int (/ cycle-time animation-secs))))
            current-image))
        (assoc :direction direction))))

