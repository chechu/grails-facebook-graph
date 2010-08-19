package com.daureos.facebook

import org.springframework.web.servlet.support.RequestContextUtils as RCU

class FacebookGraphTagLib {
	static namespace = "fbg"
	
	def resources = {attrs, body ->
		def locale = RCU.getLocale(request)
		if(!locale) locale = Locale.getDefault()

		if(!grailsApplication.config.facebook.applicationId) {
			log.error("facebook.applicationId not defined in the Config.groovy!")
		} else {
		
			out << "<div id= \"fb-root\"></div>"
			out << "<script type=\"text/javascript\" src=\"http://connect.facebook.net/${locale}/all.js\"></script>"
			
			out << "<script type=\"text/javascript\">"
			out << "FB.init({appId: '${grailsApplication.config.facebook.applicationId}', status:${attrs.status?:true}, cookie:${attrs.cookie?:true}, xfbml:${attrs.xfbml?:true}});"
			out << "</script>"
		}
	}
}
