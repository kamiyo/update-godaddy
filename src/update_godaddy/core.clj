(ns update-godaddy.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [overtone.at-at :as at-at]
            [environ.core :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]))

(def my-pool (at-at/mk-pool))
(def GDKEY (env :godaddy-key))
(def GDSECRET (env :godaddy-secret))
(def auth-header (str "sso-key " GDKEY ":" GDSECRET))

(defn dnsapi-url
  "Build the godaddy dns api resource url from type, record, and domain."
  [type record domain]
  (str "https://api.godaddy.com/v1/domains/" domain "/records/" type "/" record))

(defn cache-file-name
  "Build the cache file name based on type, record, and domain."
  [type record domain]
  (str "/tmp/.updategodaddy." type "." record "." domain ".addr"))

(defn write-cache
  "Writes the new ip address to the cache, overwriting previous address."
  [type record domain new-ip]
  (spit (cache-file-name type record domain) new-ip :append false))

(defn update-record
  "Updates the dns record on godaddy if different from detected IP (new-ip)"
  [type record domain new-ip]
  (let [response (client/get
                  (dnsapi-url type record domain)
                  {:headers {:authorization auth-header}})
        dns (-> response
                (:body)
                (json/read-str :key-fn keyword)
                (nth 0)
                (:data))]
    (println "dns-record:" dns)
    (write-cache type record domain new-ip)
    (if (not= dns new-ip)
      (let [body (json/write-str
                  [{:data new-ip
                    :ttl 3600}])
            response (client/put
                      (dnsapi-url type record domain)
                      {:headers {:authorization auth-header
                                 :content-type "application/json"}
                       :body body
                       :throw-entire-message? true})]
        (println response))
      (println "DNS record is the same as server IP"))))

(defn check-and-get-cache
  "If cache file exists, read the address in it."
  [type record domain]
  (let [cache-file (cache-file-name type record domain)]
    (if (.exists (io/file cache-file))
      (clojure.string/trim-newline (slurp cache-file))
      nil)))

(defn check-record [type record domain api]
  "For the DNS type (A or AAAA) check if server public IP as fetched from the API
  matches the cached one. If not, then update the record. Else, do nothing."
  (let [cache  (check-and-get-cache type record domain)
        public (-> api
                   (client/get)
                   (:body)
                   (json/read-str :key-fn keyword)
                   (:ip))
        valid? (case type
                 ; Super crude verification of address.
                 ; Prevents updating ipv6 record with ipv4 address and vice versa.
                 "A" (clojure.string/includes? public ".")
                 "AAAA" (clojure.string/includes? public ":"))]
    (println "cache:" cache "public:" public)
    (if (not valid?)
      (println "Address returned from" api "not valid"
               (case type
                 "A" "IP"
                 "AAAA" "IPv6")
               "address.")
      (condp = public
        nil    (println "No IP address found.")
        cache  (println "IP address unchanged.")
        (update-record type record domain public)))))

(def cli-options
  [["-e" "--every SECONDS" "Check interval in seconds (minimum 1)"
    :default 60
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 1 %) "Must be an int at least 1"]]
   ["-4" "--ipv4" "Update A record"]
   ["-6" "--ipv6" "Update AAAA record"]
   [nil "--api" "API to use for detecting IPv4 address" :default "https://api.ipify.org?format=json"]
   [nil "--api6" "API to use for detecting IPv6 address" :default "https://api6.ipify.org?format=json"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["This program checks your server IP every interval and sends an API request to GoDaddy to update the DNS record"
        ""
        "Usage: [ENV_VARS=env_vars] java -jar update-godaddy.jar [options] record domain"
        ""
        "Options:"
        options-summary
        ""
        "record: dns record to update, e.g. www, subdomain, etc."
        "domain: domain name of the dns, e.g. mydomain.com"]
       (clojure.string/join "\n")))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join "\n" errors)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        [record domain] arguments]
    (cond
      (:help options) {:exit-message (usage summary) :ok? true}
      errors {:exit-message (error-msg errors)}
      (and (nil? (:ipv4 options))
           (nil? (:ipv6 options))) {:exit-message (clojure.string/join
                                                   "\n"
                                                   ["Specify at least one of -4 or -6."
                                                    (usage summary)])}
      (or (nil? record)
          (nil? domain)) {:exit-message (clojure.string/join
                                         "\n"
                                         ["Must include record and domain."
                                          (usage summary)])}
      :else {:options options
             :arguments arguments})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "Entry. This utility checks the server's ip address at a specified interval,
  and if needed updates a cache file and sends a put request to GoDaddy's DNS API."
  [& args]
  (let [{:keys [options arguments exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (do 
        (if (or (nil? GDKEY)
                (nil? GDSECRET))
          (exit 1 "Godaddy key and secret are not set in env or java system var."))
        (let [{:keys [ipv4 ipv6 api api6 every]} options
              [record domain] arguments
              interval (* 1000 every)]
          (.addShutdownHook (Runtime/getRuntime) (Thread. #(at-at/stop-and-reset-pool! my-pool)))
          (if (boolean ipv4) 
            (at-at/every interval #(check-record "A" record domain api) my-pool))
          (if (boolean ipv6)
            (at-at/every interval #(check-record "AAAA" record domain api6) my-pool)))))))
