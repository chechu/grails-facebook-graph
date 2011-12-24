class FacebookGraphFilters {
	
	// Injected by grails
	def facebookGraphService
	def grailsApplication
	
	def filters = {
		
		// Checking the facebook session
		facebook(controller:"*", action:"*") {
			before = {
				def pair, sig, payload = ""
				def cookieName = "fbsr_" + grailsApplication.config.facebook.applicationId
				
				log.debug("Executing facebook filter")
				
				def cookie = request.cookies.find {
					it.name == cookieName
				}
				
				if(cookie) {
					session.facebook = facebookGraphService.validateSession(cookie.value.decodeURL())
				} else {
					session.facebook = [:] // Without cookie we remove the session data
				}
			}
		}
	}
} 
