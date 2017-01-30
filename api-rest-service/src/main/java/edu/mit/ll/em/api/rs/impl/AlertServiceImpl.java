/**
 * Copyright (c) 2008-2016, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.rs.impl;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONException;

import edu.mit.ll.em.api.rs.AlertService;
import edu.mit.ll.em.api.rs.AlertServiceResponse;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.APILogger;
import edu.mit.ll.nics.common.entity.Alert;
import edu.mit.ll.nics.common.entity.AlertUser;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.impl.AlertDAOImpl;


/**
 * Service for publishing and retrieving broadcasted alerts
 */
public class AlertServiceImpl implements AlertService {

	private static String SUCCESS = "success";
	
	
	/** Rabbit Producer for publishing alerts */
	private RabbitPubSubProducer rabbitProducer;
	
	private final AlertDAOImpl alertDAO = new AlertDAOImpl();
	
		
	@Override
	public Response postAlert(Alert alert, String username) {		
		if(alert != null) {
			try{
				return Response.ok(alertDAO.persistAlert(alert)).status(Status.OK).build();
			}catch(Exception e){
				APILogger.getInstance().e("AlertService: PostAlert", e.getMessage());
			}
		}
		
		return Response.ok("Failed: Alert parameter is null").status(Status.PRECONDITION_FAILED).build();
	}
	
	@Override
	public Response postUserAlert(AlertUser alertUser, String username) {
		
		try{
			
			int userId = alertUser.getUserid();
			int alertId = alertUser.getAlertid();
			int incidentId = alertUser.getIncidentid();

			if(this.alertDAO.persistUserAlert(alertUser)){
				String topic;
				if(userId == -1){
					topic = String.format("iweb.NICS.%s.alert", incidentId);
				}else{
					topic = String.format("iweb.NICS.%s.%s.alert", incidentId, userId);
				}
				
				this.notifyAlert(this.alertDAO.getAlert(alertId), topic);
				return Response.ok(SUCCESS).status(Status.OK).build();
			}
		}catch(Exception e){
			APILogger.getInstance().e("AlertService: PostUserAlert", e.getMessage());
		}
		return Response.ok("Failed to persist User Alert").status(Status.INTERNAL_SERVER_ERROR).build();
	}
	
	@Override
	public Response deleteAlert(int alertId, String username){
		try{
			if(this.alertDAO.delete(alertId) == 1){
				return Response.ok(SUCCESS).status(Status.OK).build();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return Response.ok("Failed to remove Alert").status(Status.INTERNAL_SERVER_ERROR).build();
	}

	
	@Override
	public Response getAlerts(int incidentId, int userId, String username) {
		
		AlertServiceResponse alertResponse = new AlertServiceResponse();
		List<Alert> alerts = null;
		try {
			alerts = alertDAO.getAlerts(incidentId, userId, username);

			alertResponse.setMessage("Successfully retrieved alerts.");
			alertResponse.setResults(alerts);
			
			return Response.ok(alertResponse).status(Status.OK).build();
			
		} catch(Exception e) {
			return Response.ok("Failed to retrieve alerts: " + e.getMessage()).status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}
	
	/**
	 * Publishes alert to specified topic
	 * 
	 * @param alert Alert entity to publish
	 * @param topic Topic to publish Alert entity to
	 * @throws IOException
	 * @throws JSONException
	 */
	private void notifyAlert(Alert alert, String topic) throws IOException, JSONException {
		ObjectMapper mapper = new ObjectMapper();
		String message = mapper.writeValueAsString(alert);
		
		getRabbitProducer().produce(topic, message);
	}
	
	/**
	 * Get Rabbit producer to send message
	 * 
	 * @return
	 * @throws IOException
	 */
	private RabbitPubSubProducer getRabbitProducer() throws IOException {
		if (rabbitProducer == null) {
			rabbitProducer = RabbitFactory.makeRabbitPubSubProducer(
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
		}
		return rabbitProducer;
	}

}
