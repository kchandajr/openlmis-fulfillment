package org.openlmis.fulfillment.web;

import static org.openlmis.fulfillment.i18n.MessageKeys.ERROR_PROOF_OF_DELIVERY_ALREADY_SUBMITTED;

import org.openlmis.fulfillment.domain.Order;
import org.openlmis.fulfillment.domain.OrderStatus;
import org.openlmis.fulfillment.domain.ProofOfDelivery;
import org.openlmis.fulfillment.domain.Template;
import org.openlmis.fulfillment.repository.OrderRepository;
import org.openlmis.fulfillment.repository.ProofOfDeliveryRepository;
import org.openlmis.fulfillment.service.ExporterBuilder;
import org.openlmis.fulfillment.service.FulfillmentException;
import org.openlmis.fulfillment.service.JasperReportsViewService;
import org.openlmis.fulfillment.service.PermissionService;
import org.openlmis.fulfillment.service.TemplateService;
import org.openlmis.fulfillment.web.util.ProofOfDeliveryDto;
import org.openlmis.fulfillment.web.util.ReportUtils;
import org.openlmis.fulfillment.web.validator.ProofOfDeliveryValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.jasperreports.JasperReportsMultiFormatView;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

@Controller
public class ProofOfDeliveryController extends BaseController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProofOfDeliveryController.class);
  private static final String PRINT_POD = "Print POD";

  @Autowired
  private JasperReportsViewService jasperReportsViewService;

  @Autowired
  private TemplateService templateService;

  @Autowired
  private ProofOfDeliveryRepository proofOfDeliveryRepository;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private ExporterBuilder exporter;

  @Autowired
  private ProofOfDeliveryValidator validator;

  @Autowired
  private PermissionService permissionService;

  /**
   * Allows creating new proofOfDeliveries.
   * If the id is specified, it will be ignored.
   *
   * @param pod A proofOfDelivery bound to the request body
   * @return ResponseEntity containing the created proofOfDelivery
   */
  @RequestMapping(value = "/proofOfDeliveries", method = RequestMethod.POST)
  public ResponseEntity createProofOfDelivery(@RequestBody ProofOfDeliveryDto pod,
                                              OAuth2Authentication authentication)
      throws MissingPermissionException {
    ProofOfDelivery proofOfDelivery = ProofOfDelivery.newInstance(pod);

    if (!authentication.isClientOnly()) {
      permissionService.canManagePod(proofOfDelivery);
    }

    LOGGER.debug("Creating new proofOfDelivery");
    proofOfDelivery.setId(null);
    ProofOfDelivery newProofOfDelivery = proofOfDeliveryRepository.save(proofOfDelivery);

    LOGGER.debug("Created new proofOfDelivery with id: " + pod.getId());
    return new ResponseEntity<>(
        ProofOfDeliveryDto.newInstance(newProofOfDelivery, exporter),
        HttpStatus.CREATED
    );
  }

  /**
   * Get all proofOfDeliveries.
   *
   * @return ProofOfDeliveries.
   */
  @RequestMapping(value = "/proofOfDeliveries", method = RequestMethod.GET)
  @ResponseBody
  public ResponseEntity<Collection<ProofOfDeliveryDto>> getAllProofOfDeliveries(
      OAuth2Authentication authentication) throws MissingPermissionException {
    Iterable<ProofOfDelivery> proofOfDeliveries = proofOfDeliveryRepository.findAll();

    for (ProofOfDelivery proofOfDelivery : proofOfDeliveries) {
      canManagePod(authentication, proofOfDelivery.getId());
    }

    return new ResponseEntity<>(
        ProofOfDeliveryDto.newInstance(proofOfDeliveries, exporter),
        HttpStatus.OK
    );
  }

  /**
   * Allows updating proofOfDeliveries.
   *
   * @param proofOfDeliveryId UUID of proofOfDelivery which we want to update
   * @param dto               A proofOfDeliveryDto bound to the request body
   * @return ResponseEntity containing the updated proofOfDelivery
   */
  @RequestMapping(value = "/proofOfDeliveries/{id}", method = RequestMethod.PUT)
  public ResponseEntity updateProofOfDelivery(@PathVariable("id") UUID proofOfDeliveryId,
                                              @RequestBody ProofOfDeliveryDto dto,
                                              OAuth2Authentication authentication)
      throws MissingPermissionException {
    ProofOfDelivery proofOfDelivery = ProofOfDelivery.newInstance(dto);
    ProofOfDelivery proofOfDeliveryToUpdate =
        proofOfDeliveryRepository.findOne(proofOfDeliveryId);
    if (proofOfDeliveryToUpdate == null) {
      proofOfDeliveryToUpdate = new ProofOfDelivery();
      proofOfDeliveryToUpdate.setOrder(proofOfDelivery.getOrder());

      if (!authentication.isClientOnly()) {
        permissionService.canManagePod(proofOfDeliveryToUpdate);
      }
      LOGGER.debug("Creating new proofOfDelivery");
    } else {
      canManagePod(authentication, proofOfDeliveryId);
      LOGGER.debug("Updating proofOfDelivery with id: " + proofOfDeliveryId);
    }

    proofOfDeliveryToUpdate.updateFrom(proofOfDelivery);
    proofOfDeliveryToUpdate = proofOfDeliveryRepository.save(proofOfDeliveryToUpdate);

    LOGGER.debug("Saved proofOfDelivery with id: " + proofOfDeliveryToUpdate.getId());
    return new ResponseEntity<>(
        ProofOfDeliveryDto.newInstance(proofOfDeliveryToUpdate, exporter),
        HttpStatus.OK
    );
  }

  /**
   * Get chosen proofOfDelivery.
   *
   * @param id UUID of proofOfDelivery whose we want to get
   * @return ProofOfDelivery.
   */
  @RequestMapping(value = "/proofOfDeliveries/{id}", method = RequestMethod.GET)
  public ResponseEntity<ProofOfDeliveryDto> getProofOfDelivery(@PathVariable("id") UUID id,
                                                               OAuth2Authentication authentication)
      throws MissingPermissionException {
    ProofOfDelivery proofOfDelivery = proofOfDeliveryRepository.findOne(id);
    if (proofOfDelivery == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } else {
      canManagePod(authentication, id);
      return new ResponseEntity<>(
          ProofOfDeliveryDto.newInstance(proofOfDelivery, exporter),
          HttpStatus.OK
      );
    }
  }

  /**
   * Allows deleting proofOfDelivery.
   *
   * @param id UUID of proofOfDelivery whose we want to delete
   * @return ResponseEntity containing the HTTP Status
   */
  @RequestMapping(value = "/proofOfDeliveries/{id}", method = RequestMethod.DELETE)
  public ResponseEntity deleteProofOfDelivery(@PathVariable("id") UUID id,
                                              OAuth2Authentication authentication)
      throws MissingPermissionException {
    ProofOfDelivery proofOfDelivery = proofOfDeliveryRepository.findOne(id);
    if (proofOfDelivery == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    } else {
      canManagePod(authentication, id);
      proofOfDeliveryRepository.delete(proofOfDelivery);
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
  }

  /**
   * Print to PDF Proof of Delivery.
   *
   * @param id The UUID of the ProofOfDelivery to print
   * @return ResponseEntity with the "#200 OK" HTTP response status and Pdf file on success, or
   *         ResponseEntity containing the error description status.
   */
  @RequestMapping(value = "/proofOfDeliveries/{id}/print", method = RequestMethod.GET)
  @ResponseBody
  public ModelAndView print(HttpServletRequest request, @PathVariable("id") UUID id,
                            OAuth2Authentication authentication)
      throws FulfillmentException {
    canManagePod(authentication, id);

    Template podPrintTemplate = templateService.getByName(PRINT_POD);

    Map<String, Object> params = ReportUtils.createParametersMap();
    String formatId = "'" + id + "'";
    params.put("pod_id", formatId);

    JasperReportsMultiFormatView jasperView =
        jasperReportsViewService.getJasperReportsView(podPrintTemplate, request);

    return new ModelAndView(jasperView, params);
  }

  /**
   * Submit a Proof of Delivery.
   *
   * @param id The UUID of the ProofOfDelivery to submit
   * @return ProofOfDelivery.
   * @throws FulfillmentException if ProofOfDelivery cannot be found or is not valid or has been
   *                              submitted earlier.
   */
  @RequestMapping(value = "/proofOfDeliveries/{id}/submit", method = RequestMethod.POST)
  @ResponseBody
  public ProofOfDeliveryDto submit(@PathVariable("id") UUID id,
                                   OAuth2Authentication authentication)
      throws FulfillmentException {
    ProofOfDelivery pod = proofOfDeliveryRepository.findOne(id);

    if (null == pod) {
      throw new ProofOfDeliveryNotFoundException(id);
    }

    canManagePod(authentication, id);
    validator.validate(pod);

    Order order = pod.getOrder();

    if (OrderStatus.RECEIVED == order.getStatus()) {
      throw new ProofOfDeliverySubmitException(ERROR_PROOF_OF_DELIVERY_ALREADY_SUBMITTED);
    }

    order.setStatus(OrderStatus.RECEIVED);
    orderRepository.save(order);

    return ProofOfDeliveryDto.newInstance(pod, exporter);
  }

  private void canManagePod(OAuth2Authentication authentication, UUID id)
      throws MissingPermissionException {
    if (!authentication.isClientOnly()) {
      LOGGER.debug("Checking rights to manage POD: {}", id);
      permissionService.canManagePod(id);
    }
  }
}
