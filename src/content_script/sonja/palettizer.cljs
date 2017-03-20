(ns sonja.palettizer
  (:require [delaunay-triangulation.core :as delaunay]
            [chromex.logging :refer-macros [log]]))

(defn draw-triangles [context data width height triangles]
  (let [map-px (fn [x y] (+ (* 4 x) (* 4 width y)))
        get-px (fn [x y]
                 (let [pos (map-px x y)]
                   (mapv
                     #(aget data (+ pos %))
                     (range 3))))
        color-avg-from-right-within-triangle-vertices
        (fn [triangle]
          (map
            (fn [color-element] (int (/ color-element 3.0)))
            (reduce
              (fn [summed-color color] (map + summed-color color))
              [0 0 0]
              (map
                (fn [[x y]] (get-px (int x) (int y)))
                (map
                  (fn [point] (map #(int (- % 0.01)) point))
                  triangle)))))]
    (set! (.-strokeStyle context) "#fff")
    (doseq [triangle triangles]
      (let [triangle-edges (delaunay/edges triangle)
            [first-x first-y] (first (first triangle-edges))
            [r g b] (color-avg-from-right-within-triangle-vertices triangle)]
        (.beginPath context)
        (.moveTo context first-x first-y)
        (doseq [[[a b] [to-x to-y]] triangle-edges]
          (.lineTo context to-x to-y))
        (set! (.-fillStyle context) (str "rgb(" r ", " g ", " b ")"))
        (.closePath context)
        (.fill context)
        (.stroke context)))))

(defn new-positions [points width height]
  (let [random-function (fn [] (- (rand-int 40) 20))
        within (fn [value border] (and (> value 0) (< value border)))
        new-position-if-within
        (fn [current-x current-y]
          (let [new-x (+ current-x (random-function))
                new-y (+ current-y (random-function))]
            [(if (within new-x width)
               new-x
               current-x)
             (if (within new-y height)
               new-y
               current-y)]))
        not-on-border (fn [value border] (and (not= value 0) (not= value border)))
        new-position-if-not-on-border
        (fn [position-function [current-x current-y]]
          (if (and
                (not-on-border current-x width)
                (not-on-border current-y height))
            (position-function current-x current-y)
            [current-x current-y]))]
    (map
      (partial new-position-if-not-on-border new-position-if-within)
      points)))

(defn update-frame [context data current-points future-points w h i]
  (let [frames 100.0
        moving-function (fn [cur fut]
                          (* (/ i frames) (- fut cur)))
        moved-points (map
                       (fn [[current-x current-y] [next-x next-y]]
                         [(+ current-x (moving-function current-x next-x))
                          (+ current-y (moving-function current-y next-y))])
                       current-points
                       future-points)]
    (set! (.-fillStyle context) "#fff")
    (.fillRect context 0 0 w h)
    (draw-triangles
      context
      data
      w
      h
      (:triangles
        (delaunay/triangulate moved-points)))
    (js/setTimeout
      (fn []
        (if (< i frames)
          (update-frame context data current-points future-points w h (inc i))
          (update-frame context data future-points (new-positions future-points w h) w h 0)))
      20)))

(defn run [context width height]
  (let [edge-points [[0 0] [width 0] [width height] [0 height]]
        rand-points (concat edge-points (map (fn [] [(rand-int width) (rand-int height)]) (range 20)))]
    (update-frame
      context
      (.-data (.getImageData context 0 0 width height))
      rand-points
      (new-positions rand-points width height)
      width
      height
      0)))
