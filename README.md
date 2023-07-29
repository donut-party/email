[![Clojars Project](https://img.shields.io/clojars/v/party.donut/email.svg)](https://clojars.org/party.donut/email)

# donut.email

This library defines an API for constructing emails independent of whatever
email service you use. Features include:

* Use email text and HTML email templates for email body
* Use text template for email subject
* Set global options (like `:from`) and easily override those options
* Parameterizes the email sending function so you can replace it with a mock in
  tests
* Parameterizes template rendering function so you can use something other than
  selmer if you want

# Usage

There are two steps to sending an email:

1. Taking _build options_ as input to produce _send options_. Build options
   include things like `:template-path` and `:subject-template` which are used
   to construct a _send options_ map.
2. The _send options_ map is passed to a _send function_ to perform the actual
   side-effecting send.

This library provides the `build-and-send-email-fn` which constructs a function
that combines these two steps. This is the main API entry point for the library.

Example usage:

```clojure
(defn mailgun-send-email
  [opts]
  ;; call function that actually performs the POST request or whatever for
  ;; sending an email
  )

(def send-email
  (build-email-and-send-fn
   send-email
   selmer.parser/render
   {:from "info@yourdomain.com"
    :subject "Update from YourDomain"}))

(def username "coolperson42")

(send-email {:to "user@place.com"
             :text (str "welcome " username "!")
             :subject-template "welcome {username}!"
             :data {:username "coolperson42"}})

;; text-template is used to produce the value for the :text key
(send-email {:to "user@place.com"
             :text-template "welcome {username}!"
             :subject-template "welcome {username}!"
             :data {:username username}})

;; This will look up "email-templates/welcome.html"
;; and "email-templates/welcome.txt" to produce the value for the `:text` and
;; `:html` keys
(send-email {:to "user@place.com"
             :subject-template "welcome {username}!"
             :data {:username username}
             :template-name :welcome
             :template-dir "email-templates"})
```

`build-email-and-send-fn` takes three arguments:
1. a send function, which performs the side effects of actually sending an email
2. a render function, which takes a template string and a data map as arguments
   to render strings for the email subject and body
3. a map of build options. These are treated as defaults. When you call the
   function which `build-email-and-send-fn` returns (`send-email` above), you
   can pass in a build options map which gets merged into the default build
   options.

`mailgun-send-email` is an example of a send function, and it takes a map of
send options as its argument.


## build options

donut.email library understands the following build options:

| key                 | description                                                                   |
|---------------------|-------------------------------------------------------------------------------|
| `:to`               | "to" email address                                                            |
| `:from`             | "from" email address                                                          |
| `:subject`          | email subject                                                                 |
| `:subject-template` | a string that can be used to interpolate values, like `"hi, {{ username! }}"` |
| `:headers`          | email headers                                                                 |
| `:html`             | html part of the body. optional. `:text` used if `:html` not included         |
| `:text`             | text-only part of the body                                                    |
| `:template-name`    | keyword that's used to a) look up build options and b) read body templates    |
| `:data`             | map used to render templates                                                  |

These options are used to construct _send options_ according to the rules below.
You can also include other keys in the build options map. These will get passed
to the send function. You would do this to e.g. include an option like
`:mailgun-url` or `:aws-ses` which your send function would use to interact with
an email client.

### Combining the default options with the `build-opts` multimethod

You can extend the `build-opts` multimethod to provide default build options for
a template, identified by `:template-name`. For example:

``` clojure
(defmethod build-opts :welcome
  [_]
  {:subject-template "welcome {username}"})

(build-opts {:template-name "meg"}) 
; => "welcome meg"
```

These options get merged with the default options (defined when when you call
`build-email-and-send-fn`) and callsite options (passed to `send-fn`) like so:

``` clojure
;; define build options for the welcome template
(defmethod build-opts :welcome
  [_]
  {:subject-template "welcome {username}"})

(def send-email
  (build-email-and-send-fn
   send-email
   selmer.parser/render
   {:from "info@yourdomain.com"} ;; these are the default-options
   ))

;; when you call send-email, internally the various build options get merge
;; like:
;; (merge default-options default-options call-site-options)

(send-email {:to "person@place.com"
             :data {:username "newuser"}})

;; internally this merges these values to construct build options:
(merge {:from "info@yourdomain.com"} ;; default options
       {:subject-template "welcome {username}"} ;; from build-opts multimethod
       {:to "person@place.com"
        :data {:username "newuser"}} ;; from call to send-email
       )
```

### Constructing `:subject`

If you provide a `:subject` key, that is used as a subject. Otherwise
`:subject-template` is used to generate a value for the `:subject` key using the
`:data` map

### Constructing `:text` and `:html`

If you provide `:text` and `:html` keys, those are used. Otherwise donut.email
attempts to render by looking up `:template-dir/:template-name.txt` and
`:template-dir/:template-name.txt`. The render-fn is called like this:

```
(render-fn (slurp (io/resource template-location)) data)
```

`:template-dir`, `:template-name`, and `:data` should be included in
`build-opts`.
