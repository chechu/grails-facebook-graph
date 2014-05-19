# Facebook graph plugin

This plugin provides access to the [Facebook Graph API] and makes easier the development of a single sign-on using the Facebook [Authentication proposal].

Source code: <https://github.com/chechu/grails-facebook-graph>. Collaborations are welcome :-)

## Configuration
Firstly, you should create a new application in the [Facebook developments page]. At the end of this process you will get an application id, an application secret code and an API key. Then, go to your Config.groovy file and add the application id and the application secret code:

    facebook.applicationSecret='<value>'
    facebook.applicationId='<value>'

Running the grails application locally, you may see this error returned from Facebook:

    API Error Code: 100
    API Error Description: Invalid parameter
    Error Message: next is not owned by the application.

In a nuthshell, Facebook cannot complete the call back the url specified in the settings of your FB application config. As a workaround, you can set the domain up in your hosts file. See this [thread] for more information.

Now, your application is ready to interact with Facebook using their Graph API.

##Single Sign-on with Facebook accounts
First, read the section "Single Sign-on with the JavaScript SDK" in <http://developers.facebook.com/docs/authentication>. It is a good explanation about what we want to get. Read it, please.

The plugin provides a tag that should be included in all pages (the main template is a good place):

    <fbg:resources/>

You can set an optional locale object in this tag, for instance:

    <fbg:resources locale="${Locale.getDefault()}" />

This tag adds the appropiate \<script\> tag according to the request locale (or the locale set as attribute) and the call to FB.init function, needed to keep updated the Facebook cookie. The default options used in the FB.init call are {status:true, cookie: true, xfbml: true}. You can provide specific values for these attributes, for instance:

    <fbg:resources locale="${Locale.getDefault()}" status="false" />

The code inserted by fbg:resources by default is:

    <div id="fb-root"></div>
    <script src="http://connect.facebook.net/en_US/all.js"></script>
    <script>
      FB.init({appId: 'your app id', cookie: true, xfbml: true, status: true});
    </script>

If you prefer to use https instead of http in the facebook url to get the all.js file, set the configure property facebook.secure to true in your Config.groovy , with the applicationSecret and applicationId.

Now you are ready to add your "Login with Facebook" button. First, add the fb namespace in your <html> tag (again, the main layout is a good place):

    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:fb="http://www.facebook.com/2008/fbml">

Then add the facebook login button where you want:

<fb:login-button perms="email,publish_stream" onlogin="facebookLogin();" size="large">
	<g:message code="auth.login.facebook"/>
</fb:login-button>

Read this page to know more about permissions. The facebookLogin function is the Javascript function that will be called when the Facebook login ends successfully. An example of this function (that you should provide):

    <script type="text/javascript">
    	function facebookLogin() {
    		FB.getLoginStatus(function(response) {
    			if (response.session) {
    				// logged in and connected user, someone you know
    				window.location ="${createLink(controller:'auth', action:'facebookLogin')}";
    			}
    		});
    	}
    </script>

With this function, if the facebook login has success, the application will redirect the browser to the action /auth/facebookLogin . To end the process, in this action I usually recover an object of the domain class that represents an user in my application (eg: User.groovy ). You could have a facebookUID attribute in this User class and you will have in this point a value in session.facebook.uid with the facebook UID of the authenticated user. Make a simple search in User and you will get your User object associated with the Facebook user authenticated.

If you don't locate any User with this facebookUID it means that the Facebook authenticated user is new in your application, so you should create a new User object, associate the Facebook UID stored in session.facebook.uid and save it. Congrats, you have a new user from Facebook.

    The session.facebook map is maintains by a filter added in the plugin. All will be right if you include the tag <fbg:resources/> in your pages 

##FacebookGraphService
The plugin provides this service to facilitate the access to the Facebook Graph API. Inject it in your artifacts declaring this attribute:

    def facebookGraphService

If you have the <fbg:resources/> in your pages and have a valid Facebook session, the filter added in the plugin will keep updated the map session.facebook , with information about your Facebook session. That is the unique requirement to use properly the FacebookGraphService . If you haven't got a valid session.facebook map the methods of the service will return null.

###FacebookGraphService.getFacebookProfile()
This method returns the Facebook profile information about the user associated with the map in session.facebook . The Graph URL invoked finally is https://graph.facebook.com/me and the result is in JSON format.

###FacebookGraphService.getFriends()
This method returns a list with all the friends of the user associated with the map in session.facebook . The Graph URL invoked finally is https://graph.facebook.com/me/friends and the result is in JSON format.

###FacebookGraphService.publishWall(message)
This method publishes the message passed as parameter in the wall of the user associated with the map in session.facebook . The Graph URL where the post is made finally is https://graph.facebook.com/me/feed. The parameter should be a String.

###FacebookGraphService.publishWall(map = [:])
Since 0.7 version. This method publishes the map passed as parameter in the wall of the user associated with the map in session.facebook . The Graph URL where the post is made finally is https://graph.facebook.com/me/feed. The expected parameter is a map, so you can provide more than the message. For instance:

    facebookGraphService.publishWall(message:"The message",
               link:"http://www.example.com/article.html",
               name:"The name of the link")

You can see the complete list of supported arguments in this [Facebook documentation page].

###FacebookGraphService.getProfilePhotoSrc(facebookUID)
This method generates the url of the public picture associated with the Facebook profile whose UID is equals to the passed parameter. It is a useful method to get the pictures of the Facebook friends of a user (first we call the getFriends method and then we call the getProfilePhotoSrc method for each friend).

###FacebookGraphService.api(path, facebookData, params = [:], method = 'GET')
This is the basic method to interact with Facebook Graph API.

    path: The relative path that will be concated to https://graph.facebook.com URL to invoke the API method. See http://developers.facebook.com/docs/api for more information about valid paths.
    facebookData: The map stored in session.facebook .
    params: if they are needed by the method to invoke.
    method: GET (default) or POST (to publish content)

##Using in a Tag

Create a tag in grails-app/taglib/

    import grails.converters.JSON
    class FacebookTagLib {
      def facebookGraphService

      def fbInfo = { attrs -> if (session.facebook) { def myInfo = JSON.parse     (facebookGraphService.getFacebookProfile().toString() )

    out << "<br/>id" << myInfo.id out << "<br/>first_name:" << myInfo.first_name out << "<br/>Last Name:" << myInfo.last_name out << "<br/>Gender:" << myInfo.gender out << "<br/>Timezone:" << myInfo.timezone out << "<br/>Home Town:" << myInfo.hometown out << "<br/>Link:" << myInfo.link out << "<br/>Photo:" << "<img src='${facebookGraphService.getProfilePhotoSrc(myInfo.id);}'/>"

    } else { out << "Not logged in to Facebook" } }
    
    }

and then in your view you can just use the following to display Facebook information about the currently logged in user

    <g:fbInfo/>

[Facebook Graph API]: http://developers.facebook.com/docs/api
[Authentication proposal]: http://developers.facebook.com/docs/authentication
[Facebook developments page]: http://www.facebook.com/developers
[thread]: http://forum.developers.facebook.net/viewtopic.php?id=24390
[Facebook documentation page]: http://developers.facebook.com/docs/reference/api/post