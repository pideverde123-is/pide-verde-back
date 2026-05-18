package com.pideeverde.pideverde;

import org.springframework.web.bind.annotation.CrossOrigin;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*") // <-- Dejamos solo UNA llave maestra
public class LeadController {

    @Autowired
    private LeadRepository leadRepository;

    @PostMapping
    public ResponseEntity<String> registrarLead(@Valid @RequestBody Lead lead) {
        // Doble validación de seguridad
        if (!lead.getCorreo().endsWith("@uaemex.mx") && !lead.getCorreo().endsWith("@alumno.uaemex.mx")) {
            return ResponseEntity.badRequest().body("{\"message\": \"Dominio no válido\"}");
        }
        
        try {
            leadRepository.save(lead);
            
            // Lanzamos notificaciones asíncronas para no congelar tu UI
            notificarCRM(lead);
            notificarVentas(lead);
            
            return ResponseEntity.ok("{\"message\": \"Guardado correctamente\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"message\": \"Error en servidor\"}");
        }
    }

    private void notificarCRM(Lead lead) {
        try {
            String token = System.getenv("HUBSPOT_TOKEN"); 
            String json = String.format("{\"properties\": {\"firstname\": \"%s\", \"email\": \"%s\", \"company\": \"%s\"}}", lead.getNombre(), lead.getCorreo(), lead.getFacultad());
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hubapi.com/crm/v3/objects/contacts"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    private void notificarVentas(Lead lead) {
    try {
        String token = System.getenv("SENDGRID_TOKEN"); 
        
        String json = String.format("{\"personalizations\": [{\"to\": [{\"email\": \"pideverde123@gmail.com\"}]}],\"from\": {\"email\": \"pideverde123@gmail.com\"},\"subject\": \"Nuevo Lead: %s\",\"content\": [{\"type\": \"text/plain\", \"value\": \"Facultad: %s\"}]}", lead.getNombre(), lead.getFacultad());
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        
        // CAMBIO CLAVE: Cambiamos sendAsync por send (Síncrono)
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Imprimimos el código de respuesta en los Logs de Render para auditar
        System.out.println("=== RESPUESTA DE SENDGRID CÓDIGO: " + response.statusCode() + " ===");
        System.out.println("Cuerpo de respuesta: " + response.body());
        
    } catch (Exception e) {
        System.out.println("=== ERROR CRÍTICO DE SENDGRID ===");
        e.printStackTrace(); 
    }       
}