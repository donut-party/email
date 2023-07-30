(ns donut.email
  (:require
   [clojure.java.io :as io]
   [donut.sugar.utils :as u]
   [selmer.parser :as selmer]
   [malli.core :as m]
   [malli.error :as me]))

(def EmailSchema
  [:re {:description   "https://github.com/gfredericks/test.chuck/issues/46"
        :gen/fmap      '(constantly "random@example.com")
        :error/message "Please enter an email address"}
   #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$"])

(def OptsInputSchema
  [:map
   [:to {:optional true} EmailSchema]
   [:from {:optional true} EmailSchema]
   [:data {:optional true} :map]
   [:subject {:optional true} :string]
   [:subject-template {:optional true} :string]
   [:headers {:optional true} :string]
   [:html {:optional true} :string]
   [:text {:optional true} :string]
   [:template-name {:optional true} [:or :keyword :string]]])

(def OptsOutputSchema
  [:and
   [:map
    [:to EmailSchema]
    [:from EmailSchema]
    [:subject :string]
    [:headers {:optional true} :string]]
   [:or
    [:map
     [:html :string]]
    [:map
     [:text :string]]]])

(defn- template-path
  [template-name template-format template-dir]
  (str template-dir "/" (name template-name) "." (name template-format)))

(defn- render-body-template
  [template-format {:keys [html-template text-template data template-dir template-name render]}]
  {:pre [template-format]}
  (cond
    (and (= :html template-format) html-template)
    (render html-template data)

    (and (= :text template-format) text-template)
    (render text-template data)

    :else
    (when-let [template (and template-name
                             (io/resource (template-path template-name
                                                         (if (= :text template-format) :txt :html)
                                                         template-dir)))]
      (render (slurp template) data))))

(defn render-subject
  [{:keys [subject-template data render]}]
  (when subject-template
    (render subject-template data)))

(defmulti template-build-opts (fn [{:keys [template-name]}] template-name))

(defmethod template-build-opts :default
  [opts]
  opts)

(defn- render-opts
  [{:keys [render subject html text] :as opts}]
  (if render
    (merge opts
           {:subject (or subject (render-subject opts))
            :html    (or html (render-body-template :html opts))
            :text    (or text (render-body-template :text opts))})
    opts))

(defn- merge-email-opts
  [m1 m2]
  (cond-> m1
    (or (:subject m1) (:subject-template m1)) (dissoc :subject :subject-template)
    (or (:text m1) (:text-template m1)) (dissoc :text :text-template)
    (or (:html m1) (:html-template m1)) (dissoc :html :html-template)
    true (merge m2)))

(defn- build-send-opts
  [opts default-build-opts]
  (let [email-opts (-> default-build-opts
                       (merge-email-opts (template-build-opts opts))
                       (merge-email-opts opts)
                       render-opts)]
    (when-let [explanation (m/explain OptsOutputSchema email-opts)]
      (throw (ex-info "Could not build valid email opts"
                      {:spec-explain-human (me/humanize explanation)
                       :spec-explain       explanation})))
    email-opts))

(defn build-email-and-send-fn
  [send default-build-opts]
  (fn build-email-and-send
    ([opts]
     (build-email-and-send nil opts))
    ([template-name opts]
     (send (build-send-opts (assoc opts :template-name template-name)
                            default-build-opts)))))

(def EmailComponent
  #:donut.system{:start  (fn [{{:keys [send default-build-opts]} :donut.system/config}]
                           (build-email-and-send-fn send default-build-opts))
                 :config {:send identity
                          :default-build-opts {:render selmer/render
                                               :template-dir "donut/email-templates"}}})

(defn email-component
  [config]
  (update EmailComponent :donut.system/config u/deep-merge config))
