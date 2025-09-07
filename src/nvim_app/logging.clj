(ns nvim-app.logging
  (:require
   [nvim-app.state :refer [app-config]]
   [nvim-app.utils :refer [ex-format fetch-request]]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.tools.logging.impl :as impl]
   [clojure.core.async :as a]))

(def telegram-chan (a/chan (a/sliding-buffer 100)))

(defn escape-markdown [text]
  (str/replace text #"([_*\[\]()~`>#+\-=|{}.!])" "\\\\$1"))

(defn truncate [s max-len]
  (if (> (count s) max-len)
    (str (subs s 0 max-len) "\n…")
    s))

(defn send-telegram-request
  [message & {:keys [verbose] :or {verbose false}}]

  (let [token (-> app-config :telegram :bot-token)
        chat-id (-> app-config :telegram :chat-id)
        url (str "https://api.telegram.org/bot" token "/sendMessage")

        params {:chat_id chat-id
                :text (truncate message 4000)
                :parse_mode "MarkdownV2"}
        response (fetch-request {:method :post
                                 :url url
                                 :form-params params
                                 :content-type :json
                                 :verbose verbose})
        body (:body response)]

    (if (and (= 200 (:status response)) (:ok body))
      true
      (when verbose
        (log/error "No-notify" "Failed to send telegram message: " (:errors response) (:description body))))))

(defn start-telegram-worker []
  (a/go-loop [errors-count 0]
    (when-let [msg (and (< errors-count 5) (a/<! telegram-chan))]
      (let [next-errors-count
            (try
              (let [resp (send-telegram-request msg :verbose true)]
                (if (:errors resp) (inc errors-count) 0))
              (catch Exception e
                (log/error "No-notify" "Telegram notification failed:\n" (ex-format e))
                (a/<! (a/timeout (rand-int 5000)))
                (inc errors-count)))]
        (recur next-errors-count)))))

(defn format-log-message [level throwable message]
  (let [level-str (str "*\\[" (str/capitalize (name level)) "\\]*")
        msg (escape-markdown message)
        exc (when throwable (escape-markdown (.getMessage throwable)))]
    (str level-str "\n" msg (when exc (str "\n`" exc "`")))))

(defn notify-with-telegram [level throwable message]
  (when (and
         (not= "No-notify" (first (str/split message #" ")))
         (= level :error))
    (a/put! telegram-chan (format-log-message level throwable message))))

(defn telegram-logger [logger-name]
  (let [delegate (impl/get-logger (impl/find-factory) logger-name)]
    (reify impl/Logger
      (enabled? [_ level] (impl/enabled? delegate level))
      (write! [_ level throwable message]
        (impl/write! delegate level throwable message)
        (#'notify-with-telegram level throwable message)))))

(defrecord TelegramLoggerFactory []
  impl/LoggerFactory
  (name [_] "telegram-factory")
  (get-logger [_ logger-name]
    (telegram-logger logger-name)))

(defn wrap-logging-with-notifications []
  (log/info "Wrapping logging with telegram notifications")
  (alter-var-root #'log/*logger-factory* (constantly (->TelegramLoggerFactory)))
  (start-telegram-worker))

(comment
  (wrap-logging-with-notifications)
  (log/warn  :gnore "test" "test")
  (log/error "Test error message" {:a 1})
  (log/error "No-notify" "Test error message" {:a 1})
  (log/errorf "Test error message %s" {:a 1}))
