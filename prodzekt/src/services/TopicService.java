package services;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;




import utils.Config.Role;
import beans.Report;
import beans.Subforum;
import beans.Topic;
import beans.User;
import database.Database;

@Path("/topics")
public class TopicService {

	@Context
	HttpServletRequest request;
	
	@Context
	ServletContext context;
	
	Database db = Database.getInstance();
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<Topic> getTopics(){
		List<Topic> topics = new ArrayList<Topic>();
		for(Subforum subforum:db.getSubforums()){
			topics.addAll(subforum.getTopics());
		}
		return topics;
	}
	
	@POST
	@Path("/{subforumId}")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.TEXT_PLAIN)
	public String addTopic(@PathParam("subforumId") int subforumId, @FormParam("topicName") String topicName,
							@FormParam("topicContent") String topicContent) {
		
		HttpSession session = request.getSession();
		
		User user = (User) session.getAttribute("user");
		
		if(topicName == "" || topicName == null || topicContent == "" || topicContent == null) {
			return "All fields must be filled!";
		}
		
		if(user != null) {
			for(Subforum subforum : db.getSubforums()) {
				if(subforum.getSubforumId() == subforumId) {
					Topic topic = new Topic(topicName, user, topicContent, Integer.toString(subforumId));
					subforum.addTopic(topic);
					db.saveDatabase();
					
					return "Topic created!";
				}
			}
		}
		
		return "Must be logged in to create topic!";
	}
	
	@DELETE
	@Path("/{subforumId}/{topicId}")
	@Produces(MediaType.TEXT_PLAIN)
	public String deleteTopic(@PathParam("subforumId") int subforumId, @PathParam("topicId") int topicId) {
		HttpSession session = request.getSession();
		
		User user = (User) session.getAttribute("user");
		
		if(user != null) {
			if(user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR) {
				for(Subforum subforum : db.getSubforums()) {
					if(subforum.getSubforumId() == subforumId) {
						for(Topic topic : subforum.getTopics()) {
							if(topic.getTopicId() == topicId) {
								Report report;
								if((report=db.searchReport(topicId))!=null){
									db.getReports().remove(report);
								}
								subforum.getTopics().remove(topic);
								
								// Delete from saved users
								for(User usr : db.getUsers()) {
									if(usr.getSavedTopics().containsKey(topicId)) {
										usr.getSavedTopics().remove(topicId);
									}
									if(usr.getLikedTopics().containsKey(topicId)) {
										usr.getLikedTopics().remove(topicId);
									}
									if(usr.getDislikedTopics().containsKey(topicId)) {
										usr.getDislikedTopics().remove(topicId);
									}
								}
								
								db.saveDatabase();
								
								return "Topic deleted!";
							}
						}
					}
				}
				
			} else {
				return "Must be admin or moderator to delete topic";
			}
		}
	
		return "Must be logged in to delete topic!";
	}
	
	@PUT
	@Path("/like/{subforumId}/{topicId}")
	@Produces(MediaType.TEXT_PLAIN)
	public String likeTopic(@PathParam("subforumId") int subforumId, @PathParam("topicId") int topicId) {
		HttpSession session = request.getSession();
		
		User user = (User) session.getAttribute("user");
		
		if(user != null) {
			
			for(Subforum subforum : db.getSubforums()) {
				if(subforum.getSubforumId() == subforumId) {
					for(Topic topic : subforum.getTopics()) {
						if(topic.getTopicId() == topicId) {
							if( !user.getLikedTopics().containsKey(topicId)) {
								topic.like();
								user.addLike(topicId, topic.getName());
								if(user.getDislikedTopics().containsKey(topicId)){
									user.getDislikedTopics().remove(topicId);
								}
								db.saveDatabase();
								
								return "Liked!";
							} else {
								return "Topic already liked!";
							}
						}
					}
				}
			}
		
		}
		
		return "Must be logged in to like topic!";
	}
	
	@PUT
	@Path("/dislike/{subforumId}/{topicId}")
	@Produces(MediaType.TEXT_PLAIN)
	public String dislikeTopic(@PathParam("subforumId") int subforumId, @PathParam("topicId") int topicId) {
		HttpSession session = request.getSession();
		
		User user = (User) session.getAttribute("user");
		
		if(user != null) {
			
			for(Subforum subforum : db.getSubforums()) {
				if(subforum.getSubforumId() == subforumId) {
					for(Topic topic : subforum.getTopics()) {
						if(topic.getTopicId() == topicId) {
							if( !user.getDislikedTopics().containsKey(topicId)) {
								topic.dislike();
								user.addDislike(topicId, topic.getName());
								if(user.getLikedTopics().containsKey(topicId)){
									user.getLikedTopics().remove(topicId);
								}
								db.saveDatabase();
								
								return "Dislked!";
							} else {
								return "Topic already disliked!";
							}
						}
					}
				}
			}
		
		}	
		return "Must be logged in to dislike topic!";
	}
	
	@POST
	@Path("/search/{keyWord}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public List<Topic> searchTopic(@PathParam("keyWord") String keyWord, @FormParam("topicCriteriaName") Boolean name,
			@FormParam("topicCriteriaDescription") Boolean description, @FormParam("topicCriteriaAuthor") Boolean auth,
			@FormParam("topicCriteriaSubforum") Boolean sub) {
		
		List<Topic> retVal = new ArrayList<Topic>();
		ArrayList<Subforum> subforums = (ArrayList<Subforum>) db.getSubforums();
		// setup unchecked fields
		if(name == null) {
			name = false;
		}
		if(description == null) {
			description = false;
		}
		if(auth == null) {
			auth = false;
		}
		if(sub == null) {
			sub = false;
		}
		
		
		for(Subforum subforum : subforums) {
			for(Topic topic : subforum.getTopics()) {
				if((name && topic.getName().contains(keyWord)) || (description && topic.getContent().contains(keyWord)) 
						|| (auth && topic.getAuthor().getUsername().contains(keyWord))
						|| (sub && subforum.getName().contains(keyWord))) {
					retVal.add(topic);
				}
					
			}
		}
		
		if(retVal.isEmpty()) {
			return null;
		} else {
			return retVal;
		}
	}
	
	@POST
	@Path("/save/{subforumId}/{topicId}")
	@Produces(MediaType.TEXT_PLAIN)
	public String saveTopic(@PathParam("subforumId") int subforumId, @PathParam("topicId") int topicId) {
		HttpSession session = request.getSession();
		User user = (User) session.getAttribute("user");
		
		if(user != null) {
			if(user.getSavedTopics().containsKey(topicId)) {
				return "Already saved topic!";
			}
			for(Subforum subforum : db.getSubforums()) {
				if(subforum.getSubforumId() == subforumId) {
					for(Topic topic : subforum.getTopics()) {
						if(topic.getTopicId() == topicId) {
							user.getSavedTopics().put(topicId,topic.getName());
							db.saveDatabase();

							return "Topic saved!";
						}
					}
				}
			}
		}
		
		return "Must be logged in to save the subforum!";
		
	}
	
}
