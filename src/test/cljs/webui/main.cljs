(ns webui.main
  (:require
   [dmohs.react :as r]
   ))

(r/defc ComponentWithProps
  {:render
   (fn [{:keys [this props]}]
     [:div {}
      [:h2 {} (r/get-display-name this)]
      "Hello there, " (:name props) "!"])})

(r/defc ComponentWithState
  {:get-initial-state (constantly {:click-count 0})
   :render
   (fn [{:keys [this state]}]
     [:div {}
      [:h2 {} (r/get-display-name this)]
      [:div {:style {:border "1px solid black" :padding "1rem"}
             :on-click (fn [event] (swap! state update-in [:click-count] inc))}
       "I have been clicked " (:click-count @state) " times."]])})

(r/defc ComponentWithRefs
  {:get-initial-state (constantly {:text "Some text"})
   :render
   (fn [{:keys [this state locals]}]
     [:div {}
      [:h2 {} (r/get-display-name this)]
      [:input {:style {:width "30ex"}
               :value (:text @state)
               :on-change #(swap! state assoc :text (-> % .-target .-value))
               :ref (fn [element]
                      (swap! locals assoc :input element))}]
      " " [:button {:on-click #(.select (:input @locals))} "Select"]])})

(r/defc ComponentWithSize
  {:render
   (fn [{:keys [this state]}]
     (let [{:keys [rect]} @state]
       [:div {}
        [:h2 {} (r/get-display-name this)]
        "I am this big: "
        (if rect
          (str (aget rect "height") " pixels tall and " (aget rect "width") " pixels wide!")
          "(not sure yet)")]))
   :component-did-mount
   (fn [{:keys [this state]}]
     (swap! state assoc :rect (-> this r/find-dom-node .getBoundingClientRect)))})

(r/defc ComponentWithReactWeirdness
  {:get-initial-state (constantly {:real-count 0})
   :render
   (fn [{:keys [this state]}]
     (let [{:keys [rect]} @state]
       [:div {}
        [:h2 {} (r/get-display-name this)]
        [:div {}
         "Real count: " (:real-count @state) [:br]
         "Read directly after setting state: " (or (:read-after-set @state) "?") [:br]
         "Read after calling after-update: " (or (:after-update @state) "?")]
        [:div {}
         [:button {:on-click #(this :-handle-click)} "Click Me"]]]))
   :-handle-click
   (fn [{:keys [this state]}]
     (swap! state update :real-count inc)
     (swap! state assoc :read-after-set (:real-count @state))
     (r/after-update this #(swap! state assoc :after-update (:real-count @state))))})

(r/defc FunWithEventHandlers
  {:get-initial-state (constantly {:not-equal-count 0 :saved-count 0 :bound-count 0})
   :render
   (fn [{:keys [this state locals]}]
     [:div {}
      [:h2 {} (r/get-display-name this)]
      "Click the window."
      [:div {} "This doesn't work: " (:not-equal-count @state)
       " " [:button {:on-click #(this :-toggle-anonymous)} "Toggle"]]
      [:div {} "This does: " (:saved-count @state)
       " " [:button {:on-click #(this :-toggle-saved)} "Toggle"]]
      [:div {} "This is cleaner: " (:bound-count @state)
       " " [:button {:on-click #(this :-toggle-bound)} "Toggle"]]])
   :-toggle-anonymous
   (fn [{:keys [state locals]}]
     (let [handler (fn [] (swap! state update :not-equal-count inc))]
       (if (:anonymous @locals)
         (do
           ;; This doesn't work because functions are not equal even if they have the same
           ;; definition.
           (.removeEventListener js/window "click" handler)
           (swap! locals dissoc :anonymous))
         (do
           (.addEventListener js/window "click" handler)
           (swap! locals assoc :anonymous true)))))
   :-toggle-saved
   (fn [{:keys [state locals]}]
     (when (nil? (:saved-handler @locals))
       (swap! locals assoc :saved-handler (fn [] (swap! state update :saved-count inc))))
     (if (:saved @locals)
       (do
         (.removeEventListener js/window "click" (:saved-handler @locals))
         (swap! locals dissoc :saved))
       (do
         (.addEventListener js/window "click" (:saved-handler @locals))
         (swap! locals assoc :saved true))))
   :-toggle-bound
   (fn [{:keys [this state locals]}]
     (if (:bound @locals)
       (do
         (.removeEventListener js/window "click" (r/method this :-increment-bound))
         (swap! locals dissoc :bound))
       (do
         (.addEventListener js/window "click" (r/method this :-increment-bound))
         (swap! locals assoc :bound true))))
   :-increment-bound
   (fn [{:keys [state]}]
     (swap! state update :bound-count inc))})

(r/defc App
  {:render
   (fn []
     [:div {}
      [ComponentWithProps {:name "Friendly ClojureScript Developer"}]
      [ComponentWithState]
      [ComponentWithRefs]
      [ComponentWithSize]
      [ComponentWithReactWeirdness]
      [FunWithEventHandlers]])})

(defn render-application []
  (r/render
   (r/create-element App)
   (.. js/document (getElementById "app"))))

(render-application)
