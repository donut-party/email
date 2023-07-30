(ns donut.email-test
  (:require
   [clojure.test :refer [deftest is]]
   [donut.email :as de]
   [donut.system :as ds]
   [selmer.parser :as selmer]))

(def test-send-email
  (de/build-email-and-send-fn
   identity
   {:render    selmer/render
    :template-dir "donut/email-templates"}))

(deftest renders-subject
  (is (= "hi buddy"
         (:subject (test-send-email :test {:to "test@test.com"
                                           :from "test@test.com"
                                           :subject-template "hi {{username}}"
                                           :text "hi"
                                           :data {:username "buddy"}})))))

(deftest renders-html-provided-template
  (is (= "hi buddy"
         (:html (test-send-email :test {:to "test@test.com"
                                        :from "test@test.com"
                                        :subject "hi"
                                        :html-template "hi {{username}}"
                                        :data {:username "buddy"}})))))

(deftest renders-text-provided-template
  (is (= "hi buddy"
         (:text (test-send-email :test {:to "test@test.com"
                                        :from "test@test.com"
                                        :subject "hi"
                                        :text-template "hi {{username}}"
                                        :data {:username "buddy"}})))))

(deftest renders-file-templates
  (is (= {:text "hi buddy text\n"
          :html "hi buddy html\n"}
         (select-keys
          (test-send-email :test {:to "test@test.com"
                                  :from "test@test.com"
                                  :subject "hi"
                                  :data {:username "buddy"}})
          [:text :html]))))

(def system
  #::ds{:defs
        {:services
         {:email (de/email-component {:default-build-opts {:from "hi@example.com"}})}}})

(defmethod de/template-build-opts ::donut-system-component
  [opts]
  (assoc opts :template-build-opts-works? true))

(deftest donut-system-component
  (let [email (ds/instance (ds/start system) [:services :email])]
    (is (= {:render selmer/render
            :template-name nil
            :template-dir "donut/email-templates"
            :from "hi@example.com"
            :to "user@place.com"
            :subject "test"
            :text "hi"
            :html "hi"}
           (email {:to "user@place.com"
                   :subject "test"
                   :text "hi"
                   :html "hi"})))

    (is (= {:render selmer/render
            :template-name ::donut-system-component
            :template-dir "donut/email-templates"
            :template-build-opts-works? true
            :from "hi@example.com"
            :to "user@place.com"
            :subject "test"
            :text "hi"
            :html "hi"}
           (email ::donut-system-component {:to "user@place.com"
                                            :subject "test"
                                            :text "hi"
                                            :html "hi"})))))
