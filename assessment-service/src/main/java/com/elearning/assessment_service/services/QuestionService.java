package com.elearning.assessment_service.services;

import com.elearning.assessment_service.dto.QuestionDto;
import com.elearning.assessment_service.entities.Question;
import com.elearning.assessment_service.repositories.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class QuestionService {
    private final QuestionRepository questionRepository;

    @Autowired
    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public Question createQuestion(QuestionDto questionDto) {
        Question question = new Question();
        question.setText(questionDto.getText());
        question.setOptions(questionDto.getOptions());
        question.setCorrectOption(questionDto.getCorrectOption());
        return questionRepository.save(question);
    }

    public Optional<Question> getQuestionById(Long id) {
        return questionRepository.findById(id);
    }

    public List<Question> getAllQuestions() {
        return questionRepository.findAll();
    }

    public Question updateQuestion(Long id, QuestionDto updatedQuestionDto) {
        return questionRepository.findById(id)
                .map(question -> {
                    question.setText(updatedQuestionDto.getText());
                    question.setOptions(updatedQuestionDto.getOptions());
                    question.setCorrectOption(updatedQuestionDto.getCorrectOption());
                    return questionRepository.save(question);
                }).orElse(null);
    }

    public boolean deleteQuestion(Long id) {
        if (questionRepository.existsById(id)) {
            questionRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public QuestionDto convertToDto(Question question) {
        QuestionDto dto = new QuestionDto();
        dto.setId(question.getId());
        dto.setText(question.getText());
        dto.setOptions(question.getOptions());
        dto.setCorrectOption(question.getCorrectOption());
        return dto;
    }
} 