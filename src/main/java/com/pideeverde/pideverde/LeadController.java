package com.pideeverde.pideverde;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.CrossOrigin;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*") 
public class LeadController {

    @Autowired
    private LeadRepository leadRepository;

    // 1. LISTA BLANCA DE FACULTADES PERMITIDAS (Extraídas exactamente de tu formulario HTML)
    private final List<String> FACULTADES_PERMITIDAS = Arrays.asList(
        // Área de Ciencias Naturales y Exactas
        "Facultad de Ciencias",
        "Facultad de Química",
        "Facultad de Medicina",
        "Facultad de Medicina Veterinaria y Zootecnia",
        "Facultad de Odontología",
        "Facultad de Enfermería y Obstetricia",
        
        // Área de Ingeniería y Tecnología
        "Facultad de Ingeniería",
        "Facultad de Arquitectura y Diseño",
        "Facultad de Ingeniería en Sistemas",
        
        // Área de Ciencias Sociales y Administrativas
        "Facultad de Ciencias Políticas y Sociales",
        "Facultad de Contaduría y Administración",
        "Facultad de Derecho",
        "Facultad de Economía",
        "Facultad de Turismo y Gastronomía",
        "Facultad de Trabajo Social y Humanidades",
        
        // Área de Humanidades y Artes
        "Facultad de Humanidades",
        "Facultad de Lenguas",
        "Facultad de Artes",
        "Facultad de Ciencias de la Conducta",
        "Facultad de Geografía",
        
        // Unidades Académicas Profesionales
        "UAP Atlacomulco",
        "UAP Chimalhuacán",
        "UAP Ecatepec",
        "UAP Nezahualcóyotl",
        "UAP Temascaltepec",
        "UAP Texcoco",
        "UAP Valle de Chalco",
        "UAP Valle de México",
        "UAP Valle de Teotihuacán",
        "UAP Zumpango"
    );

    @PostMapping
    public ResponseEntity<String> registrarLead(@Valid @RequestBody Lead lead) {
        
        // VALIDACIÓN 1: Dominios obligatorios de la UAEMéx
        if (!lead.getCorreo().endsWith("@uaemex.mx") && !lead.getCorreo().endsWith("@alumno.uaemex.mx")) {
            return ResponseEntity.badRequest().body("{\"message\": \"Dominio no válido\"}");
        }

        // VALIDACIÓN 2: Nombre estricto (Solo letras de la A a la Z, acentos, eñes y espacios)
        if (!lead.getNombre().matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")) {
            return ResponseEntity.badRequest().body("{\"message\": \"El nombre solo debe contener letras\"}");
        }

        // VALIDACIÓN 3: Facultad estricta (Evita alteraciones externas al formulario)
        if (!FACULTADES_PERMITIDAS.contains(lead.getFacultad())) {
            return ResponseEntity.badRequest().body("{\"message\": \"Facultad no reconocida o alterada\"}");
        }
        
        try {
            // FLUJO ASEGURADO: Primero guardamos físicamente en Clever Cloud
            Lead leadGuardado = leadRepository.save(lead);
            
            // Segundo: Mandamos los datos confirmados al CRM de HubSpot
            notificarCRM(leadGuardado);
            
            // Tercero: Despachamos únicamente la notificación interna por SendGrid (Ahorro de créditos)
            notificarVentas(leadGuardado);
            
            return ResponseEntity.ok("{\"message\": \"Guardado correctamente\"}");
            
        } catch (DataIntegrityViolationException e) { 
            // Control de duplicados por correo UNIQUE en la BD
            return ResponseEntity.badRequest().body("{\"message\": \"El correo ingresado ya se encuentra registrado.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"message\": \"Error en servidor de base de datos\"}");
        }
    }

    private void notificarCRM(Lead lead) {
        try {
            String token = System.getenv("HUBSPOT_TOKEN"); 
            // Usamos la propiedad personalizada "facultad" mapeada previamente en tu panel de HubSpot
            String json = String.format("{\"properties\": {\"firstname\": \"%s\", \"email\": \"%s\", \"facultad\": \"%s\"}}", 
                                        lead.getNombre(), lead.getCorreo(), lead.getFacultad());
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hubapi.com/crm/v3/objects/contacts"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            
            // Envío asíncrono para liberar carga al servidor principal
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }
     
    private void notificarVentas(Lead lead) {
        try {
            String token = System.getenv("SENDGRID_TOKEN"); 
            HttpClient client = HttpClient.newHttpClient();
            String correoVerificado = "pideverde123@gmail.com"; 

            // =================================================================
            // ÚNICO ENVÍO: NOTIFICACIÓN INTERNA PARA EL EQUIPO COMERCIAL
            // =================================================================
            String jsonParaMi = String.format(
                "{\"personalizations\": [{\"to\": [{\"email\": \"jolopezhu1458@uaemex.mx\"}]}],\"from\": {\"email\": \"%s\"},\"subject\": \"¡Nuevo Lead: %s!\",\"content\": [{\"type\": \"text/plain\", \"value\": \"Nombre: %s\\nCorreo: %s\\nFacultad: %s\"}]}", 
                correoVerificado, lead.getNombre(), lead.getNombre(), lead.getCorreo(), lead.getFacultad()
            );

            HttpRequest requestParaMi = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonParaMi)).build();
            
            // Procesado de manera asíncrona: optimiza el tiempo de respuesta del backend
            client.sendAsync(requestParaMi, HttpResponse.BodyHandlers.ofString());

            System.out.println("=== NOTIFICACIÓN INTERNA ENVIADA A SENDGRID PROCESADA ===");

        } catch (Exception e) {
            System.out.println("=== ERROR CRÍTICO EN ENVÍO DE NOTIFICACIÓN ===");
            e.printStackTrace(); 
        }       
    }
}