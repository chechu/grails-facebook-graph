class FacebookGraphFilters {
	
	// Injected by grails
	def facebookGraphService
	def grailsApplication
	
	def filters = {
		
		// Checking the facebook session
		facebook(controller:"*", action:"*") {
			before = {
				def pair, sig, payload = ""
				def cookieName = "fbs_" + grailsApplication.config.facebook.applicationId
				
				log.debug("Executing facebook filter")
				
				def cookie = request.cookies.find {
					it.name == cookieName
				}
				
				session.facebook = [:] // Without cookie we remove the session data
				if(cookie) {
                    def facebook = [:] // Don't write to session directly as that may cause NullPointerExceptions
					cookie.value.split("&").each{
						pair = it.split("=")
						facebook[pair[0]] = pair[1].decodeURL()
					}
					
					session.facebook = facebookGraphService.validateSession(facebook)
				}
			}
		}
	}
} 
