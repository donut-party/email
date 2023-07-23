(ns donut.email-test
  (:require
   [clojure.test :refer [deftest is]]
   [donut.email :as de]
   [selmer.parser :as selmer]))

(def test-send-email (de/combined-send-email-fn identity selmer/render {:template-dir "donut/email-templates"}))

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
