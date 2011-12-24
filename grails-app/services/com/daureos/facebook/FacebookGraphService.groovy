package com.daureos.facebook

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import grails.converters.JSON
import groovy.json.JsonSlurper
import org.springframework.web.context.request.RequestContextHolder

/**
 * See http://github.com/facebook/php-sdk
 * @author jesus.lanchas
 *
 */
class FacebookGraphService {
	
	/**
	 * If the time to the expiration is less than this threshold, a new 
	 * request is made to facebook to renew the access token
	 */
	private final static long EXPIRATION_PREVENTION_THRESHOLD = 600000 // 10 minutes

	// Injected by grails
	def grailsApplication

	boolean transactional = false
	
	/**
	 * The domains that can be used in this class.
	 */
	final static def DOMAIN_MAP = [
		api:'https://api.facebook.com/',
		api_read:'https://api-read.facebook.com/',
		graph:'https://graph.facebook.com/',
		www:'https://www.facebook.com/'
	]
		
	/**
	 * This method validates a facebook session
	 */   
	def validateSession(fbsrCookie) {
		def sig, expectedSig, data
		def result
		
		if(!grailsApplication.config.facebook.applicationSecret) {
			log.error("facebook.applicationSecret not defined in the Config.groovy")
		} else {
			data = parseSignedRequest(fbsrCookie)
			log.debug("Facebook data in the cookie: ${data}")
			
			if(!data) {
				// The fbsr cookie is not valid
				invalidateFacebookData()
			} else {
				// The session is valid
				result = createFacebookData(data)
			}
		}
		return result
	}

	/**
	 * This method returns the facebookData stored in the session
	 * associated with the user
	 */
	def getFacebookData() {
		def session = RequestContextHolder.currentRequestAttributes().getSession()
		return session?.facebook
	}

	/**
	 * This method returns the information stored by Facebook of the session user.
	 * If the session user hasn't associated a facebook session this method returns
	 * null.
	 */
	def getFacebookProfile(params = [:]) {
		def result
		applyDefaults(params)

		log.debug("Facebook data: ${params.facebookData}")
		
		if(params.facebookData) {
			try {
				result = api("/${params.id}", params.facebookData, params)
			} catch (Exception e) {
				log.error(e.message)
			}
		}
		
		return result
	}

	/**
	 * This method returns the information stored by Facebook of the object with the
	 * given ID. If no ID is given, the information of the current session user is returned.
	 * If the session user hasn't associated a facebook session this method returns
	 * null.
	 */
	def getDetails(params = [:]) {
		def result
		applyDefaults(params)

		log.debug("Facebook data: ${params.facebookData}")

		if(params.facebookData) {
			try {
				result = api("/${params.id}", params.facebookData, params)
			} catch (Exception e) {
				log.error(e.message)
			}
		}

		return result
	}

	/**
	 * This method publishes the params passed as parameter in the
	 * wall of the session user. If the session user hasn't associated
	 * a facebook session this method returns null.
	 * 
	 * Thanks to mshirman.
	 * 
	 * @param params The map of params to publish. For instance
	 * message, picture, link, name, caption, description
	 */
	def publishWall(params = [:]) {
		def result
		applyDefaults(params)

		log.debug("Facebook data: ${params.facebookData}")
		
		if(params.facebookData) {
			try {
				result = api("/${params.id}/feed", params.facebookData, params, 'POST')
			} catch (Exception e) {
				log.error(e)
			}
		}
		
		return result
	}
	
	/**
	 * This method publishes the message passed as parameter in the
	 * wall of the session user. If the session user hasn't associated 
	 * a facebook session this method returns null.
	 * 
	 * @param message The message to publish
	 */
	def publishWall(String message) {
		return publishWall(message:message)
	}

	/**
	 * This method returns the list of events in Facebook of the user with the given
	 * ID or the current session user if no ID is given.
	 * If the session user hasn't associated a facebook session this method returns null.
	 */
	def getEvents(params = [:]) {
		def result
		applyDefaults(params)

		log.debug("Facebook data: ${params.facebookData}")

		if(params.facebookData) {
			try {
				result = api("/${params.id}/events", params.facebookData, params)
			} catch (Exception e) {
				log.error(e)
			}
		}

		return result
	}

	/**
	 * This method returns the list of friends in Facebook of the session user.
	 * If the session user hasn't associated a facebook session this method returns null.
	 */
	def getFriends(params = [:]) {
		def result
		applyDefaults(params)

		log.debug("Facebook data: ${params.facebookData}")
		
		if(params.facebookData) {
			try {
				result = api("/${params.id}/friends", params.facebookData, params)
			} catch (Exception e) {
				log.error(e)
			}
		}
		
		return result
	}
	
	/**
	 * This method returns the String that can be used by an authorized user
	 * as the url of the profile image of the facebook user whose id is passed
	 * as parameter.
	 */
	def getProfilePhotoSrc(facebookUserId) {
		"${DOMAIN_MAP.graph}/${facebookUserId}/picture"
	}
	
	/**
	 * Invoke the Graph API.
	 *
	 * @param String $path the path (required)
	 * @param String $method the http method (default 'GET')
	 * @param Array $params the query/post data
	 * @return the decoded response object
	 * @throws FacebookGraphException
	 */
	def api(path, facebookData, params = [:], method = 'GET') {
		def exception
		def result
		params.method = method // method override as we always do a POST

		if(facebookData) { // without a facebook session we'll return null
			result = oauthRequest(getUrl('graph', path), params, facebookData)
			if(!result) throw new FacebookGraphException()
			else result = JSON.parse(result)
			
			log.debug("Result: ${result}")
	
			// results are returned, errors are thrown
			if (result.error) {
				exception = new FacebookGraphException(result)
				if (exception.type == 'OAuthException') {
					invalidateFacebookData()
				}
				throw exception
			}
		}
		
		return result
	}

	/**
	 * Invoke the FQL multiquery interface.
	 *
	 * @param String $queries a map of FQL queries (required)
	 * @param String $method the http method (default 'GET')
	 * @param Array $params the query/post data
	 * @return the decoded response object
	 * @throws FacebookGraphException
	 */
	def fqlMultiQuery(queries, params = [:], method = 'GET') {
		def exception
		def result
		def facebookData = params.facebookData ?: getFacebookData()
		params.method = method
		params.queries = (queries as JSON).toString() // encode queries as JSON dictionary
		params.format = "JSON"

		if (facebookData) { // without a facebook session we'll return null
			result = oauthRequest(getUrl('api_read', 'method/fql.multiquery'), params, facebookData)
			if (!result) throw new FacebookGraphException()
			else result = JSON.parse(result)

			log.debug("Result: ${result}")

			// results are returned, errors are thrown
			// the JSON for some reason returns [null] for every unset property
			if (result.error_msg && result.error_msg.class.equals(String)) {
				result.error = [message: result.error_msg]
				exception = new FacebookGraphException(result)
				if (exception.type == 'OAuthException') {
					invalidateFacebookData()
				}
				throw exception
			}
		}

		return result
	}

	/**
	 * Make a OAuth Request
	 *
	 * @param path the path (required)
	 * @param params the query/post data
	 * @return the decoded response object
	 */
	private def oauthRequest(url, params, facebookData) {
		if (!params['access_token']) {
			params['access_token'] = facebookData['access_token'];
		}

		// json_encode all params values that are not strings
		params.each{key,value ->
			if(!(value instanceof String)) params[key] = value as JSON
		}
		
		return makeRequest(url, params)
	}

	/**
	 * Makes an HTTP request.
	 *
	 * @param urlAsString the URL to make the request to
	 * @param params the parameters to use for the POST body
	 * @return String the response text
	 */
	private def makeRequest(urlAsString, params) {
		def resp
		def encodedParams = ""
		URL url
		Writer writer
		URLConnection connection
		
		// Encoding the params...
		if (params) {
			params.each{k,v ->
				if(k != 'method' && k != 'id') {
					encodedParams += k.encodeAsURL() + '=' + v.encodeAsURL() + '&'
				}
			}
			encodedParams = encodedParams[0..-2]
		}
		
		try {
			// Making the request
			switch(params.method) {
				case "GET":
					if(encodedParams) urlAsString += '?' + encodedParams
					url = new URL(urlAsString)
					resp = url.text
				break;
				case "POST":
					url = new URL(urlAsString)
					connection = url.openConnection()
					connection.setRequestMethod("POST")
					connection.doOutput = true
					
					writer = new OutputStreamWriter(connection.outputStream)
					writer.write(encodedParams)
					writer.flush()
					writer.close()
					connection.connect()
					
					if (connection.responseCode == 200 || connection.responseCode == 201)
						resp = connection.content.text
	
				break;
			}
		} catch(Exception e) {
			// resp will be null, nothing to do...
			log.error(e)
		}

		return resp
	}
 
	/**
	 * Build the URL for given domain alias, path and parameters.
	 *
	 * @param name the name of the domain
	 * @param path optional path (without a leading slash)
	 * @param params optional query parameters
	 * @return String the URL for the given parameters
	 */
	private def getUrl(name, path = '', params = [:]) {
		def url = DOMAIN_MAP[name]
		if (path) {
			if (path[0] == '/') {
				path = path.substring(1)
			}
			url += path
		}
		if (params) {
			url += '?' 
			params.each{k,v->
				url += k.encodeAsURL + '=' + v.encodeAsURL()
			}
		}
		return url
	}
	
	/**
	* It parses the signed request and it returns a json object with the signed params if the
	* sign is verify, and null otherwise.
	*
	* @see http://developers.facebook.com/docs/authentication/signed_request
	*/
   private def parseSignedRequest(String signedRequest) {
	   def result
	   byte[] payloadSig
	   String sig, payload
	   String[] pair = signedRequest.split('\\.')
	   
	   if (pair.size() != 2) {
		   log.warn "Signed request bad format: $signedRequest"
	   } else {
		   sig = unUrl(pair[0])
		   payload = unUrl(pair[1])
		   payloadSig = computePayloadSignature(payload)
		   
		   if (sig.decodeBase64() == payloadSig) {
			   result = new JsonSlurper().parseText(new String(payload.decodeBase64()))
		   }
	   }
	   return result
   }

   /**
	* It makes these replaces:
	* + by -
	* / by _
	*/
   private String unUrl(String input) {
	   return input.replace('_', '/').replace('-', '+')
   }
   
   /**
	* This method tries to recreate the facebook data from the code passed
	* by facebook after a valid authentication. If the facebook data map in
	* the current session exists, this object will be returnt. Otherwise, a
	* HTTP call will be made to get the access_token from facebook.
	*/
	private Map createFacebookData(data) {
		def params, resp, now
		def facebookData = getFacebookData()
	   
		if(!facebookData || facebookDataExpiresSoon(facebookData)) {
			params = [
				method: 'GET',
				code: data.code,
				client_id: grailsApplication.config.facebook.applicationId,
				client_secret: grailsApplication.config.facebook.applicationSecret,
				redirect_uri: ''
			]
			log.debug("Requesting the access token to facebook. Params: ${params}")
			resp = makeRequest(getUrl('graph', 'oauth/access_token'), params)
			log.debug("Response: ${resp}")
		   
			// Only with a valid response
			if(resp) {
				facebookData = [uid:data.user_id]
				resp.split('&').each {
					def pair = it.split('=')
					if(pair.size() == 2) {
						facebookData[pair[0]] = pair[1]
					}
				}
				
				// At least we need the access_token and the uid
				if(!facebookData?.access_token || !facebookData?.uid || !facebookData?.expires) {
					facebookData = null
				} else {
					// Setting the expiration date
					now = Calendar.getInstance()
					now.add(Calendar.SECOND, facebookData.expires as Integer)
					facebookData.expiresDate = now.time
				}
			}
		}
		
		return facebookData
	}
	
	/**
	 * It returns true if the facebook data passed as parameter will expire before
	 * the EXPIRATION_PREVENTION_THRESHOLD milliseconds.
	 */
	private boolean facebookDataExpiresSoon(facebookData) {
		def now = new Date()
		return facebookData &&
			facebookData.expiresDate && 
			((facebookData.expiresDate.time - now.time) < EXPIRATION_PREVENTION_THRESHOLD)
	}
	
	/**
	 * This method generates the signature of the payload passed as parameter,
	 * using the Facebook application secret as secret for the HmacSHA256 algorithm.
	 */
	byte[] computePayloadSignature(String payload) {
		String applicationSecret = grailsApplication.config.facebook.applicationSecret
		Mac mac = Mac.getInstance('HmacSHA256')
		mac.init(new SecretKeySpec(applicationSecret.bytes, 'HmacSHA256'))
		return mac.doFinal(payload.bytes)
	}

	/**
	 * Apply default values to parameters that were not given
	 */
	private def applyDefaults(params) {
		params.id = params.id ?: "me"
		params.facebookData = params.facebookData ?: getFacebookData()
	}

	/**
	 * This method set null to the facebookData attribute stored in the
	 * session object associated with the session user.
	 */
	private void invalidateFacebookData() {
		def session = RequestContextHolder.currentRequestAttributes().getSession()
		if(session) session.facebookData = null
	}
}
