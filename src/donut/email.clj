(ns donut.email
  (:require
   [clojure.java.io :as io]
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
   [:template-name {:optional true} :string]])

(def OptsOutputSchema
  [:map
   [:to EmailSchema]
   [:from EmailSchema]
   [:subject :string]
   [:headers {:optional true} :string]
   [:html :string]
   [:text :string]])

(defn- template-path
  [template-name template-format template-dir]
  (str template-dir "/" (name template-name) "." (name template-format)))

(defn render-body-template
  [template-name
   template-format
   render-fn
   {:keys [html-template text-template data template-dir]}]
  {:pre [template-name template-format]}
  (cond
    (and (= :html template-format) html-template)
    (render-fn html-template data)

    (and (= :text template-format) text-template)
    (render-fn text-template data)

    :else
    (when-let [template (io/resource (template-path template-name
                                                    (if (= :text template-format) :txt :html)
                                                    template-dir))]
      (render-fn (slurp template) data))))

(defn render-subject
  [render-fn {:keys [subject-template data]}]
  (render-fn subject-template data))

(defmulti build-opts
  (fn [template-name _opts] template-name))

(defmethod build-opts :default
  [_ opts]
  opts)

(defn- add-default-opts
  [template-name render-fn {:keys [subject html text] :as opts}]
  (merge opts
         {:subject (or subject (render-subject render-fn opts))
          :html    (or html (render-body-template template-name :html render-fn opts))
          :text    (or text (render-body-template template-name :text render-fn opts))}))

(defn build-send-opts
  [template-name render-fn opts common-opts]
  (let [email-opts (add-default-opts template-name
                                     render-fn
                                     (merge common-opts
                                            (build-opts template-name opts)))]
    (when-let [explanation (m/explain OptsOutputSchema email-opts)]
      (throw (ex-info "Could not build valid email opts"
                      {:spec-explain-human (me/humanize explanation)
                       :spec-explain       explanation})))
    email-opts))

(defn build-email-and-send-fn
  [send-fn render-fn common-opts]
  (fn build-email-and-send [template-name opts]
    (send-fn (build-send-opts template-name render-fn opts common-opts))))

(def EmailComponent
  #:donut.system{:start  (fn [{{:keys [send-fn render-fn common-opts]} :donut.system/config}]
                           (build-email-and-send-fn send-fn render-fn common-opts))
                 :config {:send-fn     identity
                          :render-fn   selmer/render
                          :common-opts {:template-dir "donut/email-templates"}}})
