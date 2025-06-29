package com.contactintegration.google.Controllers;

import com.contactintegration.google.Models.Contact;
import com.contactintegration.google.Repositories.ContactRepository;
import com.contactintegration.google.Services.GoogleContactsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/contacts")
public class ContactController {

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private GoogleContactsService googleContactsService; // Inject the Google Contacts Service

    // --- READ (Display All Local Contacts) ---
    @GetMapping
    public String listContacts(Model model) {
        List<Contact> contacts = contactRepository.findAll();
        model.addAttribute("contacts", contacts);
        return "contacts";
    }

    // --- CREATE ---
    @GetMapping("/add")
    public String showAddContactForm(Model model) {
        model.addAttribute("contact", new Contact());
        return "addContact";
    }

    @PostMapping("/add")
    public String addContact(@ModelAttribute Contact contact,
                             RedirectAttributes redirectAttributes,
                             @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        try {
            // Initialize Google API
            googleContactsService.initializePeopleService(authorizedClient);

            // Save to local DB
            Contact savedContact = contactRepository.save(contact);

            // ✅ Create in Google Contacts
            String googleId = googleContactsService.createGoogleContact(savedContact);
            if (googleId != null) {
                savedContact.setGoogleResourceId(googleId);
                contactRepository.save(savedContact); // 🔁 Save again with Google ID
                redirectAttributes.addFlashAttribute("message", "Contact added to local DB and Google Contacts.");
            } else {
                redirectAttributes.addFlashAttribute("warning", "Contact added locally, but not to Google.");
            }

        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Google Contact creation failed: " + e.getMessage());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "Google API not initialized: " + e.getMessage());
        }

        return "redirect:/contacts";
    }



    // --- UPDATE ---
    @GetMapping("/edit/{id}")
    public String showEditContactForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Contact> contact = contactRepository.findById(id);
        if (contact.isPresent()) {
            model.addAttribute("contact", contact.get());
            return "editContact";
        } else {
            redirectAttributes.addFlashAttribute("error", "Contact not found!");
            return "redirect:/contacts";
        }
    }

    @PostMapping("/update")
    public String updateContact(@ModelAttribute Contact contact,
                                RedirectAttributes redirectAttributes,
                                @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        if (contact.getId() != null && contactRepository.existsById(contact.getId())) {
            try {
                googleContactsService.initializePeopleService(authorizedClient);

                Optional<Contact> existingContactOptional = contactRepository.findById(contact.getId());
                if (existingContactOptional.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Contact not found.");
                    return "redirect:/contacts";
                }

                Contact existingContact = existingContactOptional.get();

                // Preserve Google ID
                contact.setGoogleResourceId(existingContact.getGoogleResourceId());

                // Save local
                Contact savedContact = contactRepository.save(contact);

                // Sync to Google
                if (savedContact.getGoogleResourceId() != null && !savedContact.getGoogleResourceId().isEmpty()) {
                    googleContactsService.updateGoogleContact(savedContact);
                    redirectAttributes.addFlashAttribute("message", "Updated locally and in Google Contacts.");
                } else {
                    // OPTIONAL: Create in Google if not yet synced
                    String googleId = googleContactsService.createGoogleContact(savedContact);
                    savedContact.setGoogleResourceId(googleId);
                    contactRepository.save(savedContact);
                    redirectAttributes.addFlashAttribute("message", "Google ID was missing. Created a new Google contact.");
                }

            } catch (IOException e) {
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("error", "Google update failed: " + e.getMessage());
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("error", "Google API not initialized: " + e.getMessage());
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid contact ID.");
        }
        return "redirect:/contacts";
    }



    // --- DELETE ---
    @GetMapping("/delete/{id}")
    public String deleteContact(@PathVariable Long id,
                                RedirectAttributes redirectAttributes,
                                @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        Optional<Contact> contactToDeleteOptional = contactRepository.findById(id);

        if (contactToDeleteOptional.isPresent()) {
            Contact contactToDelete = contactToDeleteOptional.get();
            try {
                // ✅ 1. Initialize GoogleContactsService with the current user's token
                googleContactsService.initializePeopleService(authorizedClient);

                // ✅ 2. Delete from Google Contacts (only if it has a Google resource ID)
                if (contactToDelete.getGoogleResourceId() != null && !contactToDelete.getGoogleResourceId().isEmpty()) {
                    googleContactsService.deleteGoogleContact(contactToDelete.getGoogleResourceId());
                }

                // ✅ 3. Delete from local database
                contactRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("message", "Contact deleted successfully from local and Google Contacts!");

            } catch (IOException e) {
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("error", "Failed to delete contact from Google Contacts: " + e.getMessage());
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("error", "Google API not initialized: " + e.getMessage());
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Contact not found!");
        }

        return "redirect:/contacts";
    }

}