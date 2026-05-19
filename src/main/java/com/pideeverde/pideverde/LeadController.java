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
        HttpClient client = HttpClient.newHttpClient();
        String correoVerificado = "pideverde123@gmail.com"; // Tu correo verificado en SendGrid

        // =================================================================
        // CORREO 1: NOTIFICACIÓN INTERNA PARA TI
        // =================================================================
        String jsonParaMi = String.format(
            "{\"personalizations\": [{\"to\": [{\"email\": \"pideverde123@gmail.com\"}]}],\"from\": {\"email\": \"%s\"},\"subject\": \"¡Nuevo Lead: %s!\",\"content\": [{\"type\": \"text/plain\", \"value\": \"Nombre: %s\\nCorreo: %s\\nFacultad: %s\"}]}", 
            correoVerificado, lead.getNombre(), lead.getNombre(), lead.getCorreo(), lead.getFacultad()
        );

        HttpRequest requestParaMi = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(jsonParaMi)).build();
        
        client.send(requestParaMi, HttpResponse.BodyHandlers.ofString());


        // =================================================================
        // CORREO 2: BIENVENIDA PREMIUM Y PERSONALIZADA PARA EL ALUMNO (HTML)
        // =================================================================
        
        // Diseñamos el cuerpo del correo con HTML, estilos en línea y una imagen representativa
        String htmlBody = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden;'>"
                + "  <div style='background-color: #2e7d32; padding: 20px; text-align: center;'>"
                + "    <h1 style='color: white; margin: 0; font-size: 24px;'>¡Bienvenido a Pide Verde! 🥗</h1>"
                + "  </div>"
                + "  <div style='padding: 20px; color: #333333; line-height: 1.6;'>"
                + "    <p style='font-size: 16px;'>Hola <b>" + lead.getNombre() + "</b>,</p>"
                + "    <p>¡Muchas gracias por sumarte a la iniciativa! Tu registro para la comunidad de la <b>" + lead.getFacultad() + "</b> ha sido procesado con éxito de manera segura.</p>"
                + "    <p>A partir de este momento, estás un paso más cerca de transformar tus hábitos alimenticios con opciones frescas, saludables y sustentables directo en tu universidad.</p>"
                + "    <div style='text-align: center; margin: 25px 0;'>"
                + "      <img src='https://i.postimg.cc/63JMXKZ4/Whats-App-Image-2026-05-18-at-21-31-19.jpg' alt='Platillo Saludable Pide Verde' style='width: 100%; max-width: 500px; border-radius: 6px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'>"
                + "    </div>"
                + "    <p style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #2e7d32; font-style: italic; margin: 20px 0;'>"
                + "      \"Tu espacio universitario, ahora más verde y saludable.\""
                + "    </p>"
                + "    <p>Pronto te enviaremos nuestras primeras dinámicas y el menú exclusivo para la UAEMéx de esta semana. ¡Mantente al pendiente!</p>"
                + "    <hr style='border: 0; border-top: 1px solid #eeeeee; margin: 20px 0;'>"
                + "    <p style='font-size: 12px; color: #777777; text-align: center;'>Este es un correo automatizado de la plataforma Pide Verde (La Hoja Feliz).<br>Por favor no respondas a este mensaje.</p>"
                + "  </div>"
                + "</div>";

        // Escapamos las comillas internas del HTML para empaquetarlo correctamente en el JSON de la API
        String jsonParaAlumno = String.format(
            "{\"personalizations\": [{\"to\": [{\"email\": \"%s\"}]}],\"from\": {\"email\": \"%s\"},\"subject\":\"¡Hola %s, tu registro en Pide Verde fue un éxito! 🎉\",\"content\": [{\"type\": \"text/html\", \"value\": \"%s\"}]}", 
            lead.getCorreo(), correoVerificado, lead.getNombre(), htmlBody.replace("\"", "\\\"")
        );

        HttpRequest requestParaAlumno = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(jsonParaAlumno)).build();

        HttpResponse<String> responseAlumno = client.send(requestParaAlumno, HttpResponse.BodyHandlers.ofString());
        System.out.println("=== STATUS ENVÍO ALUMNO: " + responseAlumno.statusCode() + " ===");

    } catch (Exception e) {
        System.out.println("=== ERROR CRÍTICO EN ENVÍO DE CORREOS ===");
        e.printStackTrace(); 
    }       
    }
}
