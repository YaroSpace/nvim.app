(ns nvim-app.integration.repos-page-test
  (:require
   [etaoin.api :as e]
   [etaoin.keys :as k]
   [nvim-app.helpers :as h]
   [clojure.test :refer [deftest testing use-fixtures is]]))

(def driver (atom nil))
(def sut nil)

(defn setup-browser [sut]
  (reset! driver (e/chrome))
  (reset! driver (e/chrome-headless))

  (doto @driver
    (e/set-window-size {:width 1280 :height 800})
    (e/go (h/get-sut-url-for sut :home))
    (e/wait-visible {:tag :h1})))

(defn setup-sut [test-fn]
  (h/with-test-system sut
    (try
      (h/setup-fixtures sut)
      (setup-browser sut)
      (test-fn)
      (finally
        (e/quit @driver)
        (reset! driver nil)))))

(use-fixtures :each setup-sut)

(deftest main
  (testing "Search and navigate to a page"
    (doto @driver
      (e/get-title))
    (is (= "Neovim Plugins Catalog" (e/get-title @driver)))))
